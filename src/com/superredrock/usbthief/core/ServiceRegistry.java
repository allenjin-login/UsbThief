package com.superredrock.usbthief.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Single orchestration point for the application's {@link Service} instances.
 *
 * <p>Services are registered in <em>startup</em> order and started by {@link #startAll()};
 * {@link #shutdownAll()} stops them in the reverse of that order, so services that consume
 * work produced by others (schedulers, sniffers) go down before the producers
 * (device manager) they depend on.
 *
 * <p>Before this class existed, the startup list lived in {@code Main.main()} and the
 * shutdown list was split between {@code Main.quit()} and {@code QueueManager.quit()},
 * which made it easy to leak a service (for example {@code SnifferLifecycleManager}) or to
 * stop one twice. Adding a service now means registering it exactly once.
 *
 * <p>All methods are idempotent and safe to call from more than one thread (for example from
 * the GUI exit action and from a JVM shutdown hook at the same time).
 */
public final class ServiceRegistry {

    private static final Logger logger = LogManager.getLogger(ServiceRegistry.class);

    private static volatile ServiceRegistry instance;

    private final List<Service> services = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public ServiceRegistry() {
    }

    /**
     * @return the application-wide registry instance
     */
    public static ServiceRegistry getInstance() {
        if (instance == null) {
            synchronized (ServiceRegistry.class) {
                if (instance == null) {
                    instance = new ServiceRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Registers a service. Registration order defines the startup order and, reversed,
     * the shutdown order. Registering the same service twice is a no-op.
     *
     * @param service the service to manage; {@code null} is ignored
     */
    public void register(Service service) {
        if (service == null) {
            logger.warn("Ignoring attempt to register a null service");
            return;
        }
        if (shutdown.get()) {
            logger.warn("Registry already shut down, ignoring registration of {}", service.getServiceName());
            return;
        }
        if (!services.contains(service)) {
            services.add(service);
            logger.debug("Registered service: {}", service.getServiceName());
        }
    }

    /**
     * Starts every registered service in registration order. Repeated calls are no-ops
     * (and an individual service ignores a duplicate {@code start()} anyway).
     */
    public void startAll() {
        if (!started.compareAndSet(false, true)) {
            logger.debug("Services already started, ignoring duplicate startAll()");
            return;
        }
        for (Service service : services) {
            try {
                service.start();
            } catch (Exception e) {
                logger.error("Failed to start service {}", service.getServiceName(), e);
            }
        }
        logger.info("Started {} services", services.size());
    }

    /**
     * Stops every registered service in reverse registration order.
     *
     * <p>Idempotent: the first invocation performs the shutdown, any later invocation
     * (for example a JVM shutdown hook firing after the GUI already exited) returns
     * immediately. A failure in one service never prevents the remaining ones from
     * being stopped.
     */
    public void shutdownAll() {
        if (!shutdown.compareAndSet(false, true)) {
            logger.debug("Services already shut down, ignoring duplicate shutdownAll()");
            return;
        }

        List<Service> ordered = new ArrayList<>(services);
        Collections.reverse(ordered);
        logger.info("Shutting down {} services", ordered.size());

        for (Service service : ordered) {
            try {
                // close() is the Closeable release hook; services that own extra
                // resources (e.g. TaskScheduler's thread pool) override it.
                service.close();
            } catch (Exception e) {
                logger.error("Failed to stop service {}", service.getServiceName(), e);
            }
        }

        logger.info("All services shut down");
    }

    /**
     * @return the number of registered services
     */
    public int size() {
        return services.size();
    }

    /**
     * @return an immutable snapshot of the registered services, in registration order
     */
    public List<Service> getServices() {
        return Collections.unmodifiableList(new ArrayList<>(services));
    }
}
