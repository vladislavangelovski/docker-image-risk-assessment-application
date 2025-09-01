package com.finki.vladislavangelovski.scan_service.core.impl;

import com.finki.vladislavangelovski.scan_service.core.ScannerException;
import com.finki.vladislavangelovski.scan_service.core.TrivyInvocationRequest;
import com.finki.vladislavangelovski.scan_service.core.TrivyInvoker;
import com.finki.vladislavangelovski.scan_service.core.TrivyOutput;

public class ProcessTrivyInvoker implements TrivyInvoker {
    @Override
    public TrivyOutput run(TrivyInvocationRequest request) throws ScannerException {
        // Minimal shell — we’ll add the real process execution next.
        // Responsibilities:
        // - Build command (trivy image ...)
        // - Set env vars for TRIVY_USERNAME/TRIVY_PASSWORD if registryCreds present
        // - Start process, enforce timeout, capture STDOUT (JSON) and bounded STDERR
        // - Map failures to ScannerException (and later, specialized subtypes)
        throw new ScannerException("Trivy invoker is not implemented yet");
    }
}
