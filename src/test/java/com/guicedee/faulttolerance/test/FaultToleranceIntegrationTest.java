package com.guicedee.faulttolerance.test;

import com.guicedee.client.IGuiceContext;
import com.guicedee.faulttolerance.FaultToleranceOptions;
import com.guicedee.faulttolerance.implementations.CircuitBreakerInterceptor;
import com.guicedee.faulttolerance.implementations.FaultTolerancePreStartup;
import org.eclipse.microprofile.faulttolerance.*;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.*;

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

    // ===================== Test Service Classes =====================

    public static class RetryService {
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

    public static class TimeoutService {
        @Timeout(value = 100, unit = java.time.temporal.ChronoUnit.MILLIS)
        public String slowMethod() {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "slow";
        }

        @Timeout(value = 5000, unit = java.time.temporal.ChronoUnit.MILLIS)
        public String fastMethod() {
            return "fast";
        }
    }

    public static class FallbackService {
        @Fallback(fallbackMethod = "fallback")
        public String riskyMethod() {
            throw new RuntimeException("Method failed");
        }

        public String fallback() {
            return "fallback-value";
        }
    }

    public static class BulkheadService {
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

    public static class CircuitBreakerService {
        @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 60000)
        public String fragileMethod() {
            throw new RuntimeException("Service down");
        }
    }

    public static class AsyncService {
        @Asynchronous
        public CompletableFuture<String> asyncMethod() {
            return CompletableFuture.completedFuture("async-result");
        }
    }

    public static class ComposedService {
        @Retry(maxRetries = 2, delay = 0)
        @Fallback(fallbackMethod = "composedFallback")
        public String retryThenFallback() {
            throw new RuntimeException("Always fails");
        }

        public String composedFallback() {
            return "composed-fallback";
        }
    }
}

