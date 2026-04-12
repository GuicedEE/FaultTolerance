package com.guicedee.faulttolerance.implementations;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

import java.lang.reflect.Method;

/**
 * Guice method interceptor for MicroProfile {@link Fallback}-annotated methods.
 *
 * <p>When the annotated method throws an exception, the interceptor invokes the specified
 * fallback method or {@link FallbackHandler} class to provide a fallback result.</p>
 */
public class FallbackInterceptor implements MethodInterceptor {

    /**
     * Creates a new fallback interceptor.
     */
    public FallbackInterceptor() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Fallback annotation = getAnnotation(invocation);
        if (annotation == null) {
            return invocation.proceed();
        }

        try {
            return invocation.proceed();
        } catch (Throwable t) {
            // Try fallbackMethod first
            String fallbackMethod = annotation.fallbackMethod();
            if (fallbackMethod != null && !fallbackMethod.isEmpty()) {
                return invokeFallbackMethod(invocation, fallbackMethod);
            }

            // Try FallbackHandler class
            Class<? extends FallbackHandler<?>> handlerClass = annotation.value();
            if (handlerClass != Fallback.DEFAULT.class) {
                return invokeFallbackHandler(invocation, handlerClass, t);
            }

            // No fallback configured, rethrow
            throw t;
        }
    }

    private Object invokeFallbackMethod(MethodInvocation invocation, String fallbackMethodName) throws Throwable {
        Object target = invocation.getThis();
        Class<?> targetClass = target.getClass();
        Method originalMethod = invocation.getMethod();

        // Look for a method with the same parameter types
        try {
            Method fallbackMethod = targetClass.getMethod(fallbackMethodName, originalMethod.getParameterTypes());
            fallbackMethod.setAccessible(true);
            return fallbackMethod.invoke(target, invocation.getArguments());
        } catch (NoSuchMethodException e) {
            // Try a no-arg fallback method
            try {
                Method fallbackMethod = targetClass.getMethod(fallbackMethodName);
                fallbackMethod.setAccessible(true);
                return fallbackMethod.invoke(target);
            } catch (NoSuchMethodException e2) {
                throw new IllegalStateException("Fallback method '" + fallbackMethodName + "' not found on " + targetClass.getName(), e2);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object invokeFallbackHandler(MethodInvocation invocation, Class<? extends FallbackHandler<?>> handlerClass, Throwable failure) throws Throwable {
        try {
            FallbackHandler<Object> handler = (FallbackHandler<Object>) handlerClass.getDeclaredConstructor().newInstance();
            ExecutionContext context = new ExecutionContext() {
                @Override
                public Method getMethod() {
                    return invocation.getMethod();
                }

                @Override
                public Object[] getParameters() {
                    return invocation.getArguments();
                }

                @Override
                public Throwable getFailure() {
                    return failure;
                }
            };
            return handler.handle(context);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate FallbackHandler: " + handlerClass.getName(), e);
        }
    }

    private Fallback getAnnotation(MethodInvocation invocation) {
        Method method = invocation.getMethod();
        Fallback annotation = method.getAnnotation(Fallback.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(Fallback.class);
        }
        return annotation;
    }
}

