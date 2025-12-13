package com.finki.vladislavangelovski.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class SmokeIT {

    private static final Path ENV_FILE = Path.of("..", ".env").toAbsolutePath().normalize();
    private static final Duration WAIT_TIMEOUT = Duration.ofMinutes(8);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String baseUrl;
    private String originalEnv;

    @Container
    private static final DockerComposeContainer<?> COMPOSE = new DockerComposeContainer<>(
                    Path.of("..", "docker-compose.yml").toFile())
            .withEnv(createComposeEnv())
            .withExposedService(
                    "gateway-service",
                    8080,
                    Wait.forHttp("/health")
                            .forStatusCode(200)
                            .withStartupTimeout(WAIT_TIMEOUT))
            .withLocalCompose(true);

    private static Map<String, String> createComposeEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("POSTGRES_USER", "smoke_user");
        env.put("POSTGRES_PASSWORD", "smoke_pass");
        env.put("POSTGRES_DB", "riskdb");
        env.put("INGEST_ENABLED", "true");
        env.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/riskdb");
        env.put("SPRING_DATASOURCE_USERNAME", "smoke_user");
        env.put("SPRING_DATASOURCE_PASSWORD", "smoke_pass");
        return env;
    }

    @BeforeAll
    void setUp() throws IOException {
        backupAndWriteEnvFile();
        COMPOSE.start();
        baseUrl = String.format(
                "http://%s:%d",
                COMPOSE.getServiceHost("gateway-service", 8080),
                COMPOSE.getServicePort("gateway-service", 8080));
        waitForGatewayHealth();
    }

    @AfterAll
    void tearDown() throws IOException {
        restoreEnvFile();
    }

    @Test
    void endToEnd_assessmentContainsFindingsAndCitations() throws Exception {
        JsonNode scan = submitScan("alpine:3.18");
        JsonNode findings = scan.path("findings");
        Assertions.assertThat(findings.isArray()).isTrue();
        Assertions.assertThat(findings.size()).isGreaterThan(0);

        String cveId = findings.get(0).path("cveId").asText();
        Assertions.assertThat(cveId).isNotBlank();

        seedCve(cveId);

        JsonNode assessment = requestAssessment("alpine:3.18");
        Assertions.assertThat(assessment.path("topFindings").size()).isGreaterThan(0);
        Assertions.assertThat(assessment.path("citations").size()).isGreaterThan(0);
    }

    private void waitForGatewayHealth() throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> rsp = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/health"))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (rsp.statusCode() == 200 && rsp.body().contains("\"status\":\"UP\"")) {
                return;
            }
            Thread.sleep(5000);
        }
        Assertions.fail("Gateway did not report UP within timeout");
    }

    private JsonNode submitScan(String imageRef) throws IOException, InterruptedException {
        Map<String, Object> payload = Map.of("image", imageRef);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/scans"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> rsp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertThat(rsp.statusCode()).isEqualTo(200);
        return objectMapper.readTree(rsp.body());
    }

    private void seedCve(String cveId) throws IOException, InterruptedException {
        Map<String, Object> payload = Map.of(
                "cve",
                Map.of(
                        "cveId", cveId,
                        "description", "Smoke test seed",
                        "references", new Object[] {Map.of("url", "https://nvd.nist.gov/vuln/detail/" + cveId, "source", "nvd")},
                        "cvssBaseScore", 5.0,
                        "cvssSeverity", "MEDIUM",
                        "cvssVector", "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N"),
                "score",
                Map.of(
                        "cveId", cveId,
                        "score", 0.42,
                        "percentile", 0.55,
                        "retrievedAt", java.time.Instant.now().toString()));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/cves/" + cveId))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> rsp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertThat(rsp.statusCode()).isBetween(200, 299);
    }

    private JsonNode requestAssessment(String imageRef) throws IOException, InterruptedException {
        Map<String, Object> payload = Map.of("imageRef", imageRef, "k", 3);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/ai/api/assess/image"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> rsp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertThat(rsp.statusCode()).isEqualTo(200);
        return objectMapper.readTree(rsp.body());
    }

    private void backupAndWriteEnvFile() throws IOException {
        if (Files.exists(ENV_FILE)) {
            originalEnv = Files.readString(ENV_FILE);
        }
        Files.writeString(
                ENV_FILE,
                "POSTGRES_USER=smoke_user\n"
                        + "POSTGRES_PASSWORD=smoke_pass\n"
                        + "POSTGRES_DB=riskdb\n"
                        + "INGEST_ENABLED=true\n",
                StandardCharsets.UTF_8);
    }

    private void restoreEnvFile() throws IOException {
        if (originalEnv != null) {
            Files.writeString(ENV_FILE, originalEnv, StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(ENV_FILE);
        }
    }
}
