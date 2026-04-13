package com.guicedee.faulttolerance.test;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;

public class ComposedService {
    @Retry(maxRetries = 2, delay = 0)
    @Fallback(fallbackMethod = "composedFallback")
    public String retryThenFallback() {
        throw new RuntimeException("Always fails");
    }

    public String composedFallback() {
        return "composed-fallback";
    }
}

