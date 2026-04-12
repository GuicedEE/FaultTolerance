/**
 * The GuicedEE Fault Tolerance module provides MicroProfile Fault Tolerance integration via Guice AOP interceptors.
 *
 * <p>It automatically binds interceptors for {@code @Retry}, {@code @Timeout}, {@code @CircuitBreaker},
 * {@code @Bulkhead}, {@code @Fallback}, and {@code @Asynchronous} annotations when the Guice injector is created.</p>
 *
 * <p>Configuration can be customized with the {@link com.guicedee.faulttolerance.FaultToleranceOptions} annotation
 * and overridden via environment variables.</p>
 */
module com.guicedee.faulttolerance {
    requires transitive com.guicedee.guicedinjection;
    requires transitive com.guicedee.client;
    requires transitive com.guicedee.modules.services.faulttolerance;
    requires transitive com.google.guice;

    exports com.guicedee.faulttolerance;
    exports com.guicedee.faulttolerance.implementations;

    opens com.guicedee.faulttolerance to com.google.guice, com.guicedee.client, com.guicedee.guicedinjection;
    opens com.guicedee.faulttolerance.implementations to com.google.guice, com.guicedee.client, com.guicedee.guicedinjection;

    provides com.guicedee.client.services.lifecycle.IGuiceModule with com.guicedee.faulttolerance.implementations.FaultToleranceModule;
    provides com.guicedee.client.services.lifecycle.IGuicePreStartup with com.guicedee.faulttolerance.implementations.FaultTolerancePreStartup;
}

