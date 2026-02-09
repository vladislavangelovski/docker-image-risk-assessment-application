package com.finki.vladislavangelovski.scan_service.core;

import java.nio.file.Path;
import java.time.Duration;

public record TrivyConfigInvocationRequest(Path inputPath, Duration timeout) {}
