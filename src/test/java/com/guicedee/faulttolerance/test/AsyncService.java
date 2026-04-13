package com.guicedee.faulttolerance.test;

import org.eclipse.microprofile.faulttolerance.Asynchronous;

import java.util.concurrent.CompletableFuture;

public class AsyncService {
    @Asynchronous
    public CompletableFuture<String> asyncMethod() {
        return CompletableFuture.completedFuture("async-result");
    }
}

