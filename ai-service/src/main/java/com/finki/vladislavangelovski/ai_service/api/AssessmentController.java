package com.finki.vladislavangelovski.ai_service.api;

import com.finki.vladislavangelovski.ai_service.service.AssessmentService;
import com.finki.vladislavangelovski.common.dto.AssessComposeRequest;
import com.finki.vladislavangelovski.common.dto.AssessComposeResponse;
import com.finki.vladislavangelovski.common.dto.AssessImageRequest;
import com.finki.vladislavangelovski.common.dto.AssessImageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assess")
public class AssessmentController {
  private final AssessmentService assessmentService;

  public AssessmentController(AssessmentService assessmentService) {
    this.assessmentService = assessmentService;
  }

  @PostMapping("/image")
  public AssessImageResponse assessImage(@RequestBody AssessImageRequest request) {
    if (request == null || !StringUtils.hasText(request.imageRef())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageRef is required");
    }
    return assessmentService.assessImage(
        new AssessImageRequest(request.imageRef(), request.k() != null ? request.k() : 6));
  }

  @PostMapping("/compose")
  public AssessComposeResponse assessCompose(@RequestBody AssessComposeRequest request) {
    if (request == null || !StringUtils.hasText(request.composeYaml())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "composeYaml is required");
    }

    try {
      return assessmentService.assessCompose(
          new AssessComposeRequest(
              request.composeYaml(), request.k() != null ? request.k() : 6, request.scanImages()));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  @GetMapping("/ping")
  public String ping() {
    return "ok";
  }
}
