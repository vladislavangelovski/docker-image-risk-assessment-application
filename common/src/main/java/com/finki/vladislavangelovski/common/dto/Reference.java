package com.finki.vladislavangelovski.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Reference(
        String label,
        String url
) {
}
