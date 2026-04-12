package com.guicedee.faulttolerance.implementations;

import com.guicedee.faulttolerance.FaultToleranceOptions;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

/**
 * Guice method interceptor for MicroProfile {@link Timeout}-annotated methods.
 *
 * <p>Wraps method execution with a timeout. If the method does not complete within the
 * specified duration, a {@link TimeoutException} is thrown.</p>
 */
public class TimeoutInterceptor implements MethodInterceptor {

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Creates a new timeout interceptor.
     */
    public TimeoutInterceptor() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Timeout annotation = getAnnotation(invocation);
        if (annotation == null) {
            return invocation.proceed();
        }

        long value = annotation.value();
        ChronoUnit unit = annotation.unit();

        FaultToleranceOptions options = FaultTolerancePreStartup.getOptions();
        if (value == 1000 && unit == ChronoUnit.MILLIS && options != null) {
            value = options.timeoutValue();
        }

        long timeoutMs = unit.getDuration().toMillis() * value;

        Future<Object> future = executor.submit(() -> {
            try {
                return invocation.proceed();
            } catch (Throwable t) {
                if (t instanceof Exception e) {
                    throw e;
                }
                throw new RuntimeException(t);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Method " + invocation.getMethod().getName() + " timed out after " + timeoutMs + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                throw cause;
            }
            throw e;
        }
    }

    private Timeout getAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        Timeout annotation = method.getAnnotation(Timeout.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(Timeout.class);
        }
        return annotation;
    }
}

