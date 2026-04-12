package com.guicedee.faulttolerance.implementations;

import com.guicedee.faulttolerance.FaultToleranceOptions;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Guice method interceptor for MicroProfile {@link CircuitBreaker}-annotated methods.
 *
 * <p>Implements a state machine with three states: CLOSED, OPEN, and HALF_OPEN.
 * When the failure ratio exceeds the configured threshold within the rolling window,
 * the circuit opens and subsequent calls fail fast with a {@link CircuitBreakerOpenException}.</p>
 */
public class CircuitBreakerInterceptor implements MethodInterceptor {

    private static final ConcurrentHashMap<String, CircuitBreakerState> states = new ConcurrentHashMap<>();

    /**
     * Creates a new circuit breaker interceptor.
     */
    public CircuitBreakerInterceptor() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        CircuitBreaker annotation = getAnnotation(invocation);
        if (annotation == null) {
            return invocation.proceed();
        }

        String key = invocation.getMethod().getDeclaringClass().getName() + "." + invocation.getMethod().getName();
        CircuitBreakerState state = states.computeIfAbsent(key, k -> new CircuitBreakerState());

        double failureRatio = annotation.failureRatio();
        FaultToleranceOptions options = FaultTolerancePreStartup.getOptions();
        if (failureRatio == 0.5 && options != null) {
            failureRatio = options.circuitBreakerFailureRatio();
        }

        int requestVolumeThreshold = annotation.requestVolumeThreshold();
        long delayMs = annotation.delay() * annotation.delayUnit().getDuration().toMillis();
        int successThreshold = annotation.successThreshold();
        Class<? extends Throwable>[] failOn = annotation.failOn();
        Class<? extends Throwable>[] skipOn = annotation.skipOn();

        // Check if circuit is OPEN
        if (state.isOpen()) {
            if (System.currentTimeMillis() - state.openedAt.get() >= delayMs) {
                // Transition to HALF_OPEN
                state.setHalfOpen();
            } else {
                throw new CircuitBreakerOpenException("Circuit breaker is open for " + key);
            }
        }

        try {
            Object result = invocation.proceed();

            if (state.isHalfOpen()) {
                int successes = state.halfOpenSuccesses.incrementAndGet();
                if (successes >= successThreshold) {
                    state.close();
                }
            } else {
                state.recordSuccess();
            }

            return result;
        } catch (Throwable t) {
            // Check skipOn first
            if (isInstanceOf(t, skipOn)) {
                state.recordSuccess();
                throw t;
            }

            // Check failOn
            if (isInstanceOf(t, failOn)) {
                if (state.isHalfOpen()) {
                    state.open();
                    throw t;
                }

                state.recordFailure();

                // Check if we should trip the circuit
                if (state.totalCalls.get() >= requestVolumeThreshold) {
                    double currentRatio = (double) state.failures.get() / state.totalCalls.get();
                    if (currentRatio >= failureRatio) {
                        state.open();
                    }
                }
            }

            throw t;
        }
    }

    private CircuitBreaker getAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        CircuitBreaker annotation = method.getAnnotation(CircuitBreaker.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(CircuitBreaker.class);
        }
        return annotation;
    }

    private boolean isInstanceOf(Throwable t, Class<? extends Throwable>[] classes) {
        for (Class<? extends Throwable> clazz : classes) {
            if (clazz.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resets all circuit breaker states. Useful for testing.
     */
    public static void resetAll() {
        states.clear();
    }

    /**
     * Internal state machine for a single circuit breaker.
     */
    static class CircuitBreakerState {
        enum State { CLOSED, OPEN, HALF_OPEN }

        volatile State state = State.CLOSED;
        final AtomicInteger totalCalls = new AtomicInteger(0);
        final AtomicInteger failures = new AtomicInteger(0);
        final AtomicLong openedAt = new AtomicLong(0);
        final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);

        boolean isOpen() {
            return state == State.OPEN;
        }

        boolean isHalfOpen() {
            return state == State.HALF_OPEN;
        }

        void open() {
            state = State.OPEN;
            openedAt.set(System.currentTimeMillis());
            halfOpenSuccesses.set(0);
        }

        void close() {
            state = State.CLOSED;
            totalCalls.set(0);
            failures.set(0);
            halfOpenSuccesses.set(0);
        }

        void setHalfOpen() {
            state = State.HALF_OPEN;
            halfOpenSuccesses.set(0);
        }

        void recordSuccess() {
            totalCalls.incrementAndGet();
        }

        void recordFailure() {
            totalCalls.incrementAndGet();
            failures.incrementAndGet();
        }
    }
}

