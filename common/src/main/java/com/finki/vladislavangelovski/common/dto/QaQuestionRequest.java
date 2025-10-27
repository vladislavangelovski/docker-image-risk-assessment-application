package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QaQuestionRequest(
        String question,
        String imageRef,
        Integer k
) {
}
