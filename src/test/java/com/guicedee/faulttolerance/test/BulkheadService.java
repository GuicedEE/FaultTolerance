package com.guicedee.faulttolerance.test;

import org.eclipse.microprofile.faulttolerance.Bulkhead;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BulkheadService {
    private CountDownLatch insideLatch;
    private CountDownLatch blockLatch;

    public void setLatches(CountDownLatch insideLatch, CountDownLatch blockLatch) {
        this.insideLatch = insideLatch;
        this.blockLatch = blockLatch;
    }

    @Bulkhead(2)
    public String limitedMethod() {
        if (insideLatch != null) {
            insideLatch.countDown();
        }
        try {
            if (blockLatch != null) {
                blockLatch.await(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "ok";
    }
}

