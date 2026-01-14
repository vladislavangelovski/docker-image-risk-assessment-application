package com.finki.vladislavangelovski.common.util;

import java.util.Objects;

public final class ValidationUtils {
    private ValidationUtils() {}

    public static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    public static String requireNoWhitespace(String value, String fieldName) {
        requireNonBlank(value, fieldName);
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(fieldName + " must not contain whitespace");
        }
        return value;
    }

    public static String requireDoesNotStartWithDash(String value, String fieldName) {
        requireNonBlank(value, fieldName);
        if (value.startsWith("-")) {
            throw new IllegalArgumentException(fieldName + " must not start with '-'");
        }
        return value;
    }

    public static int requireInRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        }
        return value;
    }

    public static String requireMaxLength(String value, int maxLength, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " chars");
        }
        return value;
    }
}
