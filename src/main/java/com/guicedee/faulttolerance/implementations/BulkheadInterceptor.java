package com.guicedee.faulttolerance.implementations;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Guice method interceptor for MicroProfile {@link Bulkhead}-annotated methods.
 *
 * <p>Limits the number of concurrent executions of a method using a {@link Semaphore}.
 * If the bulkhead is full, a {@link BulkheadException} is thrown immediately.</p>
 */
public class BulkheadInterceptor implements MethodInterceptor {

    private static final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    /**
     * Creates a new bulkhead interceptor.
     */
    public BulkheadInterceptor() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Bulkhead annotation = getAnnotation(invocation);
        if (annotation == null) {
            return invocation.proceed();
        }

        int maxConcurrent = annotation.value();
        String key = invocation.getMethod().getDeclaringClass().getName() + "." + invocation.getMethod().getName();
        Semaphore semaphore = semaphores.computeIfAbsent(key, k -> new Semaphore(maxConcurrent));

        if (!semaphore.tryAcquire()) {
            throw new BulkheadException("Bulkhead capacity reached for " + key);
        }

        try {
            return invocation.proceed();
        } finally {
            semaphore.release();
        }
    }

    private Bulkhead getAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        Bulkhead annotation = method.getAnnotation(Bulkhead.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(Bulkhead.class);
        }
        return annotation;
    }

    /**
     * Resets all bulkhead states. Useful for testing.
     */
    public static void resetAll() {
        semaphores.clear();
    }
}

