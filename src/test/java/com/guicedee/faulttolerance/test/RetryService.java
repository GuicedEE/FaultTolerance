package com.guicedee.faulttolerance.test;

import org.eclipse.microprofile.faulttolerance.Retry;

import java.util.concurrent.atomic.AtomicInteger;

public class RetryService {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Retry(maxRetries = 3, delay = 0)
    public String eventuallySucceeds() {
        int attempt = counter.incrementAndGet();
        if (attempt < 3) {
            throw new RuntimeException("Attempt " + attempt + " failed");
        }
        return "success";
    }

    @Retry(maxRetries = 2, delay = 0)
    public String alwaysFails() {
        throw new RuntimeException("Always fails");
    }

    public void resetCounter() {
        counter.set(0);
    }

    public int getAttemptCount() {
        return counter.get();
    }
}

