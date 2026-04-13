package com.guicedee.faulttolerance.test;

import org.eclipse.microprofile.faulttolerance.Fallback;

public class FallbackService {
    @Fallback(fallbackMethod = "fallback")
    public String riskyMethod() {
        throw new RuntimeException("Method failed");
    }

    public String fallback() {
        return "fallback-value";
    }
}

