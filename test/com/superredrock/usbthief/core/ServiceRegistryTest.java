package com.superredrock.usbthief.core;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ServiceRegistryTest {

    /** Records the order in which services were started and stopped. */
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private static class RecordingService extends Service {
        private final String name;
        private final CountDownLatch tickLatch = new CountDownLatch(1);

        RecordingService(String name) {
            this.name = name;
        }

        @Override
        protected void tick() {
            tickLatch.countDown();
        }

        @Override
        protected long getTickInterval() { return 20; }

        @Override
        protected TimeUnit getTickUnit() { return TimeUnit.MILLISECONDS; }

        @Override
        public String getServiceName() { return name; }

        @Override
        public String getDescription() { return "recording service " + name; }

        @Override
        protected void cleanup() {
            EVENTS.add("stop:" + name);
        }

        @Override
        public void start() {
            EVENTS.add("start:" + name);
            super.start();
        }
    }

    @BeforeEach
    void setUp() {
        EVENTS.clear();
    }

    @Test
    void shutdownAllStopsInReverseRegistrationOrder() {
        ServiceRegistry registry = new ServiceRegistry();
        RecordingService first = new RecordingService("first");
        RecordingService second = new RecordingService("second");
        RecordingService third = new RecordingService("third");

        registry.register(first);
        registry.register(second);
        registry.register(third);
        assertEquals(3, registry.size());

        registry.startAll();
        registry.shutdownAll();

        assertEquals(List.of(
            "start:first", "start:second", "start:third",
            "stop:third", "stop:second", "stop:first"
        ), EVENTS);
        assertEquals(ServiceState.STOPPED, first.getServiceState());
        assertEquals(ServiceState.STOPPED, second.getServiceState());
        assertEquals(ServiceState.STOPPED, third.getServiceState());
    }

    @Test
    void shutdownAllIsIdempotent() {
        ServiceRegistry registry = new ServiceRegistry();
        RecordingService service = new RecordingService("only");
        registry.register(service);
        registry.startAll();

        registry.shutdownAll();
        registry.shutdownAll();

        assertEquals(List.of("start:only", "stop:only"), EVENTS);
    }

    @Test
    void startAllIsIdempotent() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(new RecordingService("svc"));

        registry.startAll();
        registry.startAll();

        assertEquals(List.of("start:svc"), EVENTS);
    }

    @Test
    void duplicateRegistrationIsIgnored() {
        ServiceRegistry registry = new ServiceRegistry();
        RecordingService service = new RecordingService("dup");

        registry.register(service);
        registry.register(service);

        assertEquals(1, registry.size());
        assertEquals(List.of(service), registry.getServices());
    }

    @Test
    void nullRegistrationIsIgnored() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(null);
        assertEquals(0, registry.size());
    }

    @Test
    void registrationAfterShutdownIsRejected() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.shutdownAll();

        registry.register(new RecordingService("late"));
        assertEquals(0, registry.size());
    }

    @Test
    void shutdownContinuesWhenOneServiceFails() {
        ServiceRegistry registry = new ServiceRegistry();
        Service failing = new RecordingService("failing") {
            @Override
            protected void cleanup() {
                super.cleanup();
                throw new RuntimeException("boom");
            }
        };
        RecordingService other = new RecordingService("other");

        registry.register(failing);
        registry.register(other);
        registry.startAll();

        assertDoesNotThrow(registry::shutdownAll);
        assertTrue(EVENTS.contains("stop:other"));
        assertTrue(EVENTS.contains("stop:failing"));
    }
}
