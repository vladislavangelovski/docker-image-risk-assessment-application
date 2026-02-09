package com.finki.vladislavangelovski.scan_service.core.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finki.vladislavangelovski.scan_service.api.dto.Severity;
import com.finki.vladislavangelovski.scan_service.core.ParserException;
import com.finki.vladislavangelovski.scan_service.core.TrivyConfigParser;
import org.junit.jupiter.api.Test;

class JacksonTrivyConfigParserTest {
  private final TrivyConfigParser parser = new JacksonTrivyConfigParser();

  @Test
  void parsesMisconfigurationsFromTrivyConfigJson() throws Exception {
    String raw =
        """
        {
          "SchemaVersion": 2,
          "Results": [
            {
              "Target": "docker-compose.yml",
              "Class": "config",
              "Type": "docker-compose",
              "Misconfigurations": [
                {
                  "ID": "DS001",
                  "Title": "Privileged container",
                  "Description": "Avoid privileged containers.",
                  "Message": "Service runs in privileged mode.",
                  "Severity": "HIGH",
                  "PrimaryURL": "https://example.com/DS001",
                  "References": ["https://ref.example/1"],
                  "CauseMetadata": {
                    "Resource": "services.app",
                    "StartLine": 10,
                    "EndLine": 20
                  }
                }
              ]
            }
          ]
        }
        """;

    TrivyConfigParser.ParsedConfigScan parsed = parser.parse(raw);

    assertThat(parsed.findings()).hasSize(1);
    assertThat(parsed.findings().getFirst().id()).isEqualTo("DS001");
    assertThat(parsed.findings().getFirst().severity()).isEqualTo(Severity.HIGH);
    assertThat(parsed.findings().getFirst().resource()).isEqualTo("services.app");
    assertThat(parsed.findings().getFirst().startLine()).isEqualTo(10);
    assertThat(parsed.findings().getFirst().endLine()).isEqualTo(20);
    assertThat(parsed.bySeverity().get(Severity.HIGH)).isEqualTo(1);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> parser.parse("{not-json"))
        .isInstanceOf(ParserException.class)
        .hasMessageContaining("valid JSON");
  }
}
