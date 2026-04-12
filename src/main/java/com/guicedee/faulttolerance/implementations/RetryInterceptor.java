package com.guicedee.faulttolerance.implementations;

import com.guicedee.faulttolerance.FaultToleranceOptions;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Guice method interceptor for MicroProfile {@link Retry}-annotated methods.
 *
 * <p>Retries a method invocation up to {@code maxRetries} times when an exception matching
 * {@code retryOn} is thrown (and not matching {@code abortOn}). Supports configurable delay,
 * jitter, and maximum total duration.</p>
 */
public class RetryInterceptor implements MethodInterceptor {

    /**
     * Creates a new retry interceptor.
     */
    public RetryInterceptor() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Retry annotation = getAnnotation(invocation);
        if (annotation == null) {
            return invocation.proceed();
        }

        int maxRetries = annotation.maxRetries();
        FaultToleranceOptions options = FaultTolerancePreStartup.getOptions();
        if (maxRetries == 3 && options != null) {
            // Use global default if annotation uses the default value
            maxRetries = options.retryMaxRetries();
        }

        long delay = annotation.delay();
        long jitter = annotation.jitter();
        ChronoUnit delayUnit = annotation.delayUnit();
        ChronoUnit jitterUnit = annotation.jitterDelayUnit();
        long maxDuration = annotation.maxDuration();
        ChronoUnit maxDurationUnit = annotation.durationUnit();
        Class<? extends Throwable>[] retryOn = annotation.retryOn();
        Class<? extends Throwable>[] abortOn = annotation.abortOn();

        long delayMs = delayUnit.getDuration().toMillis() * delay;
        long jitterMs = jitterUnit.getDuration().toMillis() * jitter;
        long maxDurationMs = maxDurationUnit.getDuration().toMillis() * maxDuration;

        long startTime = System.currentTimeMillis();
        Throwable lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return invocation.proceed();
            } catch (Throwable t) {
                lastException = t;

                // Check abortOn first
                if (shouldAbort(t, abortOn)) {
                    throw t;
                }

                // Check retryOn
                if (!shouldRetry(t, retryOn)) {
                    throw t;
                }

                // Check if we've exceeded max duration
                if (maxDurationMs > 0 && (System.currentTimeMillis() - startTime) >= maxDurationMs) {
                    throw t;
                }

                // Check if we've exhausted retries
                if (attempt >= maxRetries) {
                    throw t;
                }

                // Apply delay with jitter
                if (delayMs > 0 || jitterMs > 0) {
                    long actualDelay = delayMs;
                    if (jitterMs > 0) {
                        actualDelay += ThreadLocalRandom.current().nextLong(-jitterMs, jitterMs + 1);
                    }
                    if (actualDelay > 0) {
                        Thread.sleep(actualDelay);
                    }
                }
            }
        }

        throw lastException;
    }

    private Retry getAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        Retry annotation = method.getAnnotation(Retry.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(Retry.class);
        }
        return annotation;
    }

    private boolean shouldAbort(Throwable t, Class<? extends Throwable>[] abortOn) {
        for (Class<? extends Throwable> clazz : abortOn) {
            if (clazz.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRetry(Throwable t, Class<? extends Throwable>[] retryOn) {
        if (retryOn.length == 0) {
            return true;
        }
        for (Class<? extends Throwable> clazz : retryOn) {
            if (clazz.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}

