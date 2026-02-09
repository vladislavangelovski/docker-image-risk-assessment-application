package com.finki.vladislavangelovski.ai_service.service;

import com.finki.vladislavangelovski.common.dto.AssessComposeRequest;
import com.finki.vladislavangelovski.common.dto.AssessComposeResponse;
import com.finki.vladislavangelovski.common.dto.AssessImageRequest;
import com.finki.vladislavangelovski.common.dto.AssessImageResponse;

public interface AssessmentService {
  AssessImageResponse assessImage(AssessImageRequest request);

  AssessComposeResponse assessCompose(AssessComposeRequest request);
}
