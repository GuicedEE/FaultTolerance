package com.guicedee.faulttolerance.test;

public class TimeoutService {
    @org.eclipse.microprofile.faulttolerance.Timeout(value = 100, unit = java.time.temporal.ChronoUnit.MILLIS)
    public String slowMethod() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "slow";
    }

    @org.eclipse.microprofile.faulttolerance.Timeout(value = 5000, unit = java.time.temporal.ChronoUnit.MILLIS)
    public String fastMethod() {
        return "fast";
    }
}

