package com.guicedee.faulttolerance.test;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

public class CircuitBreakerService {
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 60000)
    public String fragileMethod() {
        throw new RuntimeException("Service down");
    }
}

