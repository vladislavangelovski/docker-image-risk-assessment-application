package com.finki.vladislavangelovski.scan_service.core.impl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finki.vladislavangelovski.scan_service.api.dto.ConfigFinding;
import com.finki.vladislavangelovski.scan_service.api.dto.Severity;
import com.finki.vladislavangelovski.scan_service.core.ParserException;
import com.finki.vladislavangelovski.scan_service.core.TrivyConfigParser;
import java.util.*;

public class JacksonTrivyConfigParser implements TrivyConfigParser {
  private final ObjectMapper mapper;

  public JacksonTrivyConfigParser() {
    this.mapper = new ObjectMapper(new JsonFactory());
  }

  @Override
  public ParsedConfigScan parse(String rawJson) throws ParserException {
    if (rawJson == null || rawJson.isBlank()) {
      throw new ParserException("Empty scanner output");
    }

    final JsonNode root;
    try {
      root = mapper.readTree(rawJson);
    } catch (Exception e) {
      throw new ParserException("Scanner output is not valid JSON", e);
    }

    List<ConfigFinding> findings = new ArrayList<>();
    Map<Severity, Integer> bySeverity = new EnumMap<>(Severity.class);
    for (Severity s : Severity.values()) {
      bySeverity.put(s, 0);
    }

    JsonNode results = root.path("Results");
    if (results.isArray()) {
      for (JsonNode result : results) {
        String target = optText(result, "Target", "");
        JsonNode misconfigs = result.get("Misconfigurations");
        if (misconfigs == null || !misconfigs.isArray()) {
          continue;
        }

        for (JsonNode m : misconfigs) {
          String id = optText(m, "ID", null);
          String title = optText(m, "Title", null);
          String description = optText(m, "Description", null);
          String message = optText(m, "Message", null);
          Severity severity = parseSeverity(optText(m, "Severity", null));
          String primaryUrl = optText(m, "PrimaryURL", null);

          Set<String> refs = new LinkedHashSet<>();
          if (primaryUrl != null && !primaryUrl.isBlank()) {
            refs.add(primaryUrl.trim());
          }
          JsonNode refArr = m.get("References");
          if (refArr != null && refArr.isArray()) {
            for (JsonNode r : refArr) {
              if (r.isTextual() && !r.asText().isBlank()) {
                refs.add(r.asText().trim());
              }
            }
          }

          JsonNode cause = m.path("CauseMetadata");
          String resource = optText(cause, "Resource", null);
          Integer startLine = optInt(cause, "StartLine", null);
          Integer endLine = optInt(cause, "EndLine", null);

          Severity sev = severity != null ? severity : Severity.UNKNOWN;
          bySeverity.put(sev, bySeverity.getOrDefault(sev, 0) + 1);

          findings.add(
              new ConfigFinding(
                  id,
                  title,
                  description,
                  message,
                  sev,
                  primaryUrl,
                  List.copyOf(refs),
                  target,
                  resource,
                  startLine,
                  endLine));
        }
      }
    }

    return new ParsedConfigScan(findings, bySeverity);
  }

  private static Severity parseSeverity(String raw) {
    if (raw == null || raw.isBlank()) {
      return Severity.UNKNOWN;
    }
    try {
      return Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ignored) {
      return Severity.UNKNOWN;
    }
  }

  private static String optText(JsonNode node, String field, String def) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return def;
    }
    JsonNode v = node.get(field);
    if (v == null || v.isNull() || v.isMissingNode()) {
      return def;
    }
    return v.isTextual() ? v.asText() : v.toString();
  }

  private static Integer optInt(JsonNode node, String field, Integer def) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return def;
    }
    JsonNode v = node.get(field);
    if (v == null || v.isNull() || v.isMissingNode()) {
      return def;
    }
    if (!v.isNumber()) {
      return def;
    }
    int value = v.asInt();
    return value >= 0 ? value : def;
  }
}
