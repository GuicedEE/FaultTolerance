package com.guicedee.faulttolerance.implementations;

import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.lifecycle.IGuicePreStartup;
import com.guicedee.faulttolerance.FaultToleranceOptions;
import io.vertx.core.Future;

import java.util.List;

/**
 * Pre-startup hook that discovers {@link FaultToleranceOptions} from the classpath
 * and makes the resolved options available to other fault tolerance components.
 *
 * <p>This hook runs early in the GuicedEE lifecycle (sortOrder = MIN_VALUE + 45)
 * to ensure fault tolerance configuration is available before the Guice injector is created.</p>
 */
public class FaultTolerancePreStartup implements IGuicePreStartup<FaultTolerancePreStartup> {

    private static FaultToleranceOptions options;

    /**
     * Creates a new fault tolerance pre-startup handler.
     */
    public FaultTolerancePreStartup() {
    }

    /**
     * Scans for {@link FaultToleranceOptions} annotations and initializes the configuration.
     *
     * @return a list of futures representing startup completion
     */
    @Override
    public List<Future<Boolean>> onStartup() {
        var scanResult = IGuiceContext.instance().getScanResult();
        var classes = scanResult.getClassesWithAnnotation(FaultToleranceOptions.class);
        if (!classes.isEmpty()) {
            var clazz = classes.getFirst().loadClass();
            FaultToleranceOptions annotation = clazz.getAnnotation(FaultToleranceOptions.class);
            options = new FaultToleranceOptions() {
                @Override
                public Class<? extends java.lang.annotation.Annotation> annotationType() {
                    return FaultToleranceOptions.class;
                }

                @Override
                public boolean enabled() {
                    return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("FT_ENABLED", String.valueOf(annotation.enabled())));
                }

                @Override
                public int retryMaxRetries() {
                    return Integer.parseInt(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("FT_RETRY_MAX_RETRIES", String.valueOf(annotation.retryMaxRetries())));
                }

                @Override
                public long timeoutValue() {
                    return Long.parseLong(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("FT_TIMEOUT_VALUE", String.valueOf(annotation.timeoutValue())));
                }

                @Override
                public double circuitBreakerFailureRatio() {
                    return Double.parseDouble(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("FT_CIRCUIT_BREAKER_FAILURE_RATIO", String.valueOf(annotation.circuitBreakerFailureRatio())));
                }
            };
        }
        return List.of(Future.succeededFuture(true));
    }

    /**
     * Returns the resolved {@link FaultToleranceOptions}, or {@code null} if none was found.
     *
     * @return the options, or null
     */
    public static FaultToleranceOptions getOptions() {
        return options;
    }

    /**
     * Returns whether fault tolerance is enabled.
     *
     * <p>Checks the resolved options first, then falls back to the {@code FT_ENABLED} environment variable,
     * and finally defaults to {@code true}.</p>
     *
     * @return true if fault tolerance is enabled
     */
    public static boolean isEnabled() {
        if (options != null) {
            return options.enabled();
        }
        return Boolean.parseBoolean(com.guicedee.client.Environment.getSystemPropertyOrEnvironment("FT_ENABLED", "true"));
    }

    /**
     * Returns the sort order for this pre-startup hook.
     *
     * @return the sort order value
     */
    @Override
    public Integer sortOrder() {
        return Integer.MIN_VALUE + 45;
    }
}

