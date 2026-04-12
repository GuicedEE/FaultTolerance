
open module guiced.faulttolerance.test {
    requires transitive com.guicedee.faulttolerance;

    requires org.junit.jupiter.api;
    requires transitive com.guicedee.modules.services.faulttolerance;

    exports com.guicedee.faulttolerance.test;
}

