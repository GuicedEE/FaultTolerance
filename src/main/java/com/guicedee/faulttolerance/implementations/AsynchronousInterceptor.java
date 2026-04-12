package com.guicedee.faulttolerance.implementations;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.microprofile.faulttolerance.Asynchronous;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Guice method interceptor for MicroProfile {@link Asynchronous}-annotated methods.
 *
 * <p>Wraps method execution in a virtual thread. The annotated method should return
 * {@link CompletionStage}, {@link CompletableFuture}, or {@link Future}.</p>
 */
public class AsynchronousInterceptor implements MethodInterceptor {

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Creates a new asynchronous interceptor.
     */
    public AsynchronousInterceptor() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Asynchronous annotation = getAnnotation(invocation);
        if (annotation == null) {
            return invocation.proceed();
        }

        Class<?> returnType = invocation.getMethod().getReturnType();

        if (CompletionStage.class.isAssignableFrom(returnType) || CompletableFuture.class.isAssignableFrom(returnType)) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return invocation.proceed();
                } catch (Throwable t) {
                    throw new java.util.concurrent.CompletionException(t);
                }
            }, executor).thenCompose(result -> {
                if (result instanceof CompletionStage<?> cs) {
                    return (CompletionStage<Object>) cs;
                }
                return CompletableFuture.completedFuture(result);
            });
        }

        if (Future.class.isAssignableFrom(returnType)) {
            return executor.submit(() -> {
                try {
                    Object result = invocation.proceed();
                    if (result instanceof Future<?> f) {
                        return f.get();
                    }
                    return result;
                } catch (Throwable t) {
                    if (t instanceof Exception e) {
                        throw e;
                    }
                    throw new RuntimeException(t);
                }
            });
        }

        // For non-Future return types, just run async and return a Future
        return CompletableFuture.supplyAsync(() -> {
            try {
                return invocation.proceed();
            } catch (Throwable t) {
                throw new java.util.concurrent.CompletionException(t);
            }
        }, executor);
    }

    private Asynchronous getAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        Asynchronous annotation = method.getAnnotation(Asynchronous.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(Asynchronous.class);
        }
        return annotation;
    }
}

