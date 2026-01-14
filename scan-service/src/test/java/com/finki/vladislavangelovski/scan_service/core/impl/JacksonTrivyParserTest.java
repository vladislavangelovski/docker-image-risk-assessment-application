package com.finki.vladislavangelovski.scan_service.core.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finki.vladislavangelovski.scan_service.api.dto.Finding;
import com.finki.vladislavangelovski.scan_service.api.dto.Severity;
import com.finki.vladislavangelovski.scan_service.core.ParserException;
import com.finki.vladislavangelovski.scan_service.core.TrivyParser;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class JacksonTrivyParserTest {
  private final TrivyParser parser = new JacksonTrivyParser();

  @Test
  void parsesNginxOsAndAppFixtures() throws Exception {
    TrivyParser.ParsedScan parsed = parser.parse(load("trivy/nginx-1_25-os-and-app.json"));

    assertThat(parsed.image()).isEqualTo("nginx:1.25");
    assertThat(parsed.digest()).isEqualTo("nginx@sha256:deadbeef");
    assertThat(parsed.findings()).hasSize(2);
    assertThat(parsed.fixAvailable()).isEqualTo(2);
    assertThat(parsed.bySeverity().get(Severity.HIGH)).isEqualTo(1);
    assertThat(parsed.bySeverity().get(Severity.MEDIUM)).isEqualTo(1);
  }

  @Test
  void parsesMultipleTargets() throws Exception {
    TrivyParser.ParsedScan parsed = parser.parse(load("trivy/multiple-targets-os-plus-lang.json"));

    assertThat(parsed.findings()).hasSize(2);
    assertThat(parsed.bySeverity().get(Severity.CRITICAL)).isEqualTo(1);
    assertThat(parsed.bySeverity().get(Severity.LOW)).isEqualTo(1);
  }

  @Test
  void treatsEmptyFixedVersionAsNoFix() throws Exception {
    TrivyParser.ParsedScan parsed = parser.parse(load("trivy/no-fixed-version.json"));

    assertThat(parsed.fixAvailable()).isZero();
    Finding finding = parsed.findings().getFirst();
    assertThat(finding.fixedVersion()).isNull();
  }

  @Test
  void prefersNvdCvssWhenPresent() throws Exception {
    Finding finding =
        parser.parse(load("trivy/with-nvd-and-vendor-cvss.json")).findings().getFirst();

    assertThat(finding.cvss()).isNotNull();
    assertThat(finding.cvss().source()).isEqualTo("nvd");
    assertThat(finding.cvss().score()).isEqualByComparingTo(new BigDecimal("9.1"));
  }

  @Test
  void usesVendorCvssWhenNoNvd() throws Exception {
    Finding finding = parser.parse(load("trivy/with-vendor-cvss-only.json")).findings().getFirst();

    assertThat(finding.cvss()).isNotNull();
    assertThat(finding.cvss().source()).isEqualTo("redhat");
  }

  @Test
  void normalizesAndDeduplicatesReferences() throws Exception {
    Finding finding = parser.parse(load("trivy/many-references.json")).findings().getFirst();
    List<String> refs = finding.references();

    assertThat(refs)
        .contains(
            "https://nvd.nist.gov/vuln/detail/CVE-2024-0501",
            "https://example.com/advisory",
            "https://www.example.org/security");
    assertThat(refs).hasSize(3);
  }

  @Test
  void rejectsMalformedJson() throws Exception {
    String raw = load("trivy/malformed-output.json");

    assertThatThrownBy(() -> parser.parse(raw))
        .isInstanceOf(ParserException.class)
        .hasMessageContaining("valid JSON");
  }

  private String load(String name) throws IOException {
    try (InputStream in = JacksonTrivyParserTest.class.getClassLoader().getResourceAsStream(name)) {
      assertThat(in).as("fixture %s", name).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
