package com.guicedee.faulttolerance.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.faulttolerance.FaultToleranceOptions;
import com.guicedee.faulttolerance.implementations.CircuitBreakerInterceptor;
import com.guicedee.faulttolerance.implementations.FaultTolerancePreStartup;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for the GuicedEE Fault Tolerance module.
 */
@FaultToleranceOptions
public class FaultToleranceIntegrationTest {

    @BeforeEach
    void setUp() {
        CircuitBreakerInterceptor.resetAll();
    }

    // ===================== @Retry Tests =====================

    @Test
    void testRetrySucceedsAfterFailures() {
        IGuiceContext.instance().inject();
        RetryService service = IGuiceContext.get(RetryService.class);
        service.resetCounter();

        String result = service.eventuallySucceeds();
        Assertions.assertEquals("success", result);
        Assertions.assertEquals(3, service.getAttemptCount(), "Should have tried 3 times (2 failures + 1 success)");
    }

    @Test
    void testRetryExhausted() {
        IGuiceContext.instance().inject();
        RetryService service = IGuiceContext.get(RetryService.class);

        Assertions.assertThrows(RuntimeException.class, service::alwaysFails);
    }

    // ===================== @Timeout Tests =====================

    @Test
    void testTimeoutExceeded() {
        IGuiceContext.instance().inject();
        TimeoutService service = IGuiceContext.get(TimeoutService.class);

        Assertions.assertThrows(TimeoutException.class, service::slowMethod);
    }

    @Test
    void testTimeoutNotExceeded() {
        IGuiceContext.instance().inject();
        TimeoutService service = IGuiceContext.get(TimeoutService.class);

        String result = service.fastMethod();
        Assertions.assertEquals("fast", result);
    }

    // ===================== @Fallback Tests =====================

    @Test
    void testFallbackMethodInvoked() {
        IGuiceContext.instance().inject();
        FallbackService service = IGuiceContext.get(FallbackService.class);

        String result = service.riskyMethod();
        Assertions.assertEquals("fallback-value", result);
    }

    // ===================== @Bulkhead Tests =====================

    @Test
    void testBulkheadRejectsConcurrentCalls() throws Exception {
        IGuiceContext.instance().inject();
        BulkheadService service = IGuiceContext.get(BulkheadService.class);

        CountDownLatch insideLatch = new CountDownLatch(2);
        CountDownLatch blockLatch = new CountDownLatch(1);
        service.setLatches(insideLatch, blockLatch);

        ExecutorService exec = Executors.newFixedThreadPool(3);

        // Launch 2 concurrent calls (bulkhead value = 2)
        Future<?> f1 = exec.submit(() -> service.limitedMethod());
        Future<?> f2 = exec.submit(() -> service.limitedMethod());

        // Wait until both are inside the method
        insideLatch.await(5, TimeUnit.SECONDS);

        // Third call should be rejected
        Future<?> f3 = exec.submit(() -> service.limitedMethod());

        try {
            f3.get(5, TimeUnit.SECONDS);
            Assertions.fail("Should have thrown BulkheadException");
        } catch (ExecutionException e) {
            Assertions.assertInstanceOf(BulkheadException.class, e.getCause());
        }

        // Release blocked calls
        blockLatch.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        exec.shutdown();
    }

    // ===================== @CircuitBreaker Tests =====================

    @Test
    void testCircuitBreakerTrips() {
        IGuiceContext.instance().inject();
        CircuitBreakerService service = IGuiceContext.get(CircuitBreakerService.class);

        // Cause enough failures to trip the circuit (requestVolumeThreshold=4, failureRatio=0.5)
        for (int i = 0; i < 4; i++) {
            try {
                service.fragileMethod();
            } catch (RuntimeException ignored) {
            }
        }

        // Next call should get CircuitBreakerOpenException
        Assertions.assertThrows(CircuitBreakerOpenException.class, service::fragileMethod);
    }

    // ===================== @Asynchronous Tests =====================

    @Test
    void testAsynchronousExecution() throws Exception {
        IGuiceContext.instance().inject();
        AsyncService service = IGuiceContext.get(AsyncService.class);

        CompletableFuture<String> future = service.asyncMethod();
        Assertions.assertNotNull(future);
        String result = future.get(5, TimeUnit.SECONDS);
        Assertions.assertEquals("async-result", result);
    }

    // ===================== Composition Tests =====================

    @Test
    void testRetryWithFallback() {
        IGuiceContext.instance().inject();
        ComposedService service = IGuiceContext.get(ComposedService.class);

        String result = service.retryThenFallback();
        Assertions.assertEquals("composed-fallback", result);
    }

    // ===================== Configuration Tests =====================

    @Test
    void testFaultToleranceOptionsDiscovered() {
        IGuiceContext.instance().inject();
        FaultToleranceOptions options = FaultTolerancePreStartup.getOptions();
        Assertions.assertNotNull(options);
        Assertions.assertTrue(options.enabled());
        Assertions.assertEquals(3, options.retryMaxRetries());
        Assertions.assertEquals(1000, options.timeoutValue());
        Assertions.assertEquals(0.5, options.circuitBreakerFailureRatio());
    }
}
