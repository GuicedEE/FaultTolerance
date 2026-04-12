package com.guicedee.faulttolerance.implementations;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import org.eclipse.microprofile.faulttolerance.*;

/**
 * Guice module that binds fault tolerance interceptors for MicroProfile Fault Tolerance annotations.
 *
 * <p>This module registers interceptors for {@code @Retry}, {@code @Timeout}, {@code @CircuitBreaker},
 * {@code @Bulkhead}, {@code @Fallback}, and {@code @Asynchronous} annotations. Each interceptor is
 * bound to both method-level and class-level annotations.</p>
 *
 * <p>If fault tolerance is disabled via {@link FaultTolerancePreStartup#isEnabled()}, no interceptors
 * are bound.</p>
 */
public class FaultToleranceModule extends AbstractModule implements IGuiceModule<FaultToleranceModule> {

    /**
     * Creates a new fault tolerance module.
     */
    public FaultToleranceModule() {
    }

    /**
     * Configures fault tolerance interceptors for all supported annotations.
     */
    @Override
    protected void configure() {
        if (!FaultTolerancePreStartup.isEnabled()) {
            return;
        }

        // @Retry interceptor
        RetryInterceptor retryInterceptor = new RetryInterceptor();
        requestInjection(retryInterceptor);
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Retry.class), retryInterceptor);
        bindInterceptor(Matchers.annotatedWith(Retry.class), Matchers.any(), retryInterceptor);

        // @Timeout interceptor
        TimeoutInterceptor timeoutInterceptor = new TimeoutInterceptor();
        requestInjection(timeoutInterceptor);
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Timeout.class), timeoutInterceptor);
        bindInterceptor(Matchers.annotatedWith(Timeout.class), Matchers.any(), timeoutInterceptor);

        // @CircuitBreaker interceptor
        CircuitBreakerInterceptor circuitBreakerInterceptor = new CircuitBreakerInterceptor();
        requestInjection(circuitBreakerInterceptor);
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(CircuitBreaker.class), circuitBreakerInterceptor);
        bindInterceptor(Matchers.annotatedWith(CircuitBreaker.class), Matchers.any(), circuitBreakerInterceptor);

        // @Bulkhead interceptor
        BulkheadInterceptor bulkheadInterceptor = new BulkheadInterceptor();
        requestInjection(bulkheadInterceptor);
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Bulkhead.class), bulkheadInterceptor);
        bindInterceptor(Matchers.annotatedWith(Bulkhead.class), Matchers.any(), bulkheadInterceptor);

        // @Fallback interceptor
        FallbackInterceptor fallbackInterceptor = new FallbackInterceptor();
        requestInjection(fallbackInterceptor);
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Fallback.class), fallbackInterceptor);
        bindInterceptor(Matchers.annotatedWith(Fallback.class), Matchers.any(), fallbackInterceptor);

        // @Asynchronous interceptor
        AsynchronousInterceptor asynchronousInterceptor = new AsynchronousInterceptor();
        requestInjection(asynchronousInterceptor);
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Asynchronous.class), asynchronousInterceptor);
        bindInterceptor(Matchers.annotatedWith(Asynchronous.class), Matchers.any(), asynchronousInterceptor);
    }
}

