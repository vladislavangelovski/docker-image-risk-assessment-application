package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QaQuestionResponse(
    String answer, List<Citation> citations, List<String> usedCves, List<String> usedPackages) {}
