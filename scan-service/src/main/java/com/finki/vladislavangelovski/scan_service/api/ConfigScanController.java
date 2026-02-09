package com.finki.vladislavangelovski.scan_service.api;

import com.finki.vladislavangelovski.scan_service.api.dto.ConfigScanRequest;
import com.finki.vladislavangelovski.scan_service.api.dto.ConfigScanResult;
import com.finki.vladislavangelovski.scan_service.core.ConfigScanOrchestrator;
import com.finki.vladislavangelovski.scan_service.core.ParserException;
import com.finki.vladislavangelovski.scan_service.core.ScannerException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scans/config")
@Tag(
    name = "Config Scans",
    description = "Scan IaC/config files (docker-compose, Dockerfile) for misconfigurations.")
public class ConfigScanController {
  private static final int MAX_CONTENT_CHARS = 1_000_000;

  private final ConfigScanOrchestrator orchestrator;

  public ConfigScanController(ConfigScanOrchestrator orchestrator) {
    this.orchestrator = orchestrator;
  }

  @PostMapping(
      value = "/docker-compose",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Scan docker-compose.yml for misconfigurations")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Scan completed",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ConfigScanResult.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Bad request",
        content =
            @Content(
                mediaType = "application/json",
                examples =
                    @ExampleObject(
                        value =
                            """
                            {"errorCode":"BAD_REQUEST_IMAGE","message":"content is required","details":{}}
                            """)))
  })
  public ConfigScanResult scanDockerCompose(@RequestBody ConfigScanRequest request)
      throws ScannerException, ParserException {
    String content = request != null ? request.content() : null;
    validateContent(content);
    Duration timeout = durationOrDefault(request != null ? request.timeoutSec() : null);
    return orchestrator.scanDockerCompose(content, timeout);
  }

  @PostMapping(
      value = "/dockerfile",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Scan Dockerfile for misconfigurations")
  public ConfigScanResult scanDockerfile(@RequestBody ConfigScanRequest request)
      throws ScannerException, ParserException {
    String content = request != null ? request.content() : null;
    validateContent(content);
    Duration timeout = durationOrDefault(request != null ? request.timeoutSec() : null);
    return orchestrator.scanDockerfile(content, timeout);
  }

  private static void validateContent(String content) {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content is required");
    }
    if (content.length() > MAX_CONTENT_CHARS) {
      throw new IllegalArgumentException("content is too large");
    }
  }

  private static Duration durationOrDefault(Integer timeoutSec) {
    int t = timeoutSec != null ? timeoutSec : 120;
    if (t < 5 || t > 900) {
      throw new IllegalArgumentException("timeoutSec must be between 5 and 900");
    }
    return Duration.ofSeconds(t);
  }
}
