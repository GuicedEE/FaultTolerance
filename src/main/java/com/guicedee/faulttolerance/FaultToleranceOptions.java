package com.guicedee.faulttolerance;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to configure Fault Tolerance for GuicedEE.
 *
 * <p>This annotation can be placed on a configuration class to customize the global fault tolerance
 * behavior, including default retry counts, timeout values, and circuit breaker thresholds.
 *
 * <p>All attributes can be overridden via environment variables:
 * <ul>
 *     <li>{@code FT_ENABLED} — overrides {@link #enabled()}</li>
 *     <li>{@code FT_RETRY_MAX_RETRIES} — overrides {@link #retryMaxRetries()}</li>
 *     <li>{@code FT_TIMEOUT_VALUE} — overrides {@link #timeoutValue()}</li>
 *     <li>{@code FT_CIRCUIT_BREAKER_FAILURE_RATIO} — overrides {@link #circuitBreakerFailureRatio()}</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface FaultToleranceOptions {
    /**
     * Whether fault tolerance interceptors are enabled.
     *
     * <p>If set to {@code false}, all fault tolerance interceptors will be disabled and methods
     * will execute without any fault tolerance behavior.
     * Defaults to {@code true}.
     *
     * @return true if enabled, false otherwise.
     */
    boolean enabled() default true;

    /**
     * The default maximum number of retries for {@code @Retry}.
     *
     * <p>This value is used as the global default when an {@code @Retry} annotation does not
     * explicitly specify {@code maxRetries}.
     * Defaults to {@code 3}.
     *
     * @return the default maximum retry count.
     */
    int retryMaxRetries() default 3;

    /**
     * The default timeout value in milliseconds for {@code @Timeout}.
     *
     * <p>This value is used as the global default when a {@code @Timeout} annotation does not
     * explicitly specify a value.
     * Defaults to {@code 1000}.
     *
     * @return the default timeout in milliseconds.
     */
    long timeoutValue() default 1000;

    /**
     * The default failure ratio for {@code @CircuitBreaker}.
     *
     * <p>This value is used as the global default when a {@code @CircuitBreaker} annotation does not
     * explicitly specify {@code failureRatio}.
     * Defaults to {@code 0.5}.
     *
     * @return the default failure ratio (0.0 to 1.0).
     */
    double circuitBreakerFailureRatio() default 0.5;
}

