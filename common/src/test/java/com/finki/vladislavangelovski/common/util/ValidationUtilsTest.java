package com.finki.vladislavangelovski.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilsTest {
    @Test
    void requireNonBlankRejectsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ValidationUtils.requireNonBlank(null, "field"));
        assertEquals("field is required", ex.getMessage());
    }

    @Test
    void requireNonBlankRejectsBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ValidationUtils.requireNonBlank("  ", "field"));
        assertEquals("field is required", ex.getMessage());
    }

    @Test
    void requireNoWhitespaceRejectsWhitespace() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ValidationUtils.requireNoWhitespace("a b", "field"));
        assertEquals("field must not contain whitespace", ex.getMessage());
    }

    @Test
    void requireInRangeRejectsOutOfRange() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ValidationUtils.requireInRange(5, 10, 20, "timeoutSec"));
        assertEquals("timeoutSec must be between 10 and 20", ex.getMessage());
    }
}
