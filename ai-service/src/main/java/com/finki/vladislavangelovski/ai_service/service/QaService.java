package com.finki.vladislavangelovski.ai_service.service;

import com.finki.vladislavangelovski.ai_service.history.QaUserContext;
import com.finki.vladislavangelovski.common.dto.QaQuestionRequest;
import com.finki.vladislavangelovski.common.dto.QaQuestionResponse;

public interface QaService {
  QaQuestionResponse answerQuestion(QaQuestionRequest request, QaUserContext userContext);
}
