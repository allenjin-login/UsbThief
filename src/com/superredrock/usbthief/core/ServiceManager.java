package com.superredrock.usbthief.core;

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Service Manager
 * <p>
 * Responsible for loading, managing, and coordinating all services.
 * Uses ServiceLoader to automatically discover service implementations
 * and internally manages ScheduledThreadPoolExecutor for task scheduling.
 * <p>
 * Provides systemctl-like batch management functionality: start, stop, and status queries.
 * <p>
 * Uses singleton pattern, consistent with QueueManager and ConfigManager.
 */
public class ServiceManager {

    protected static final Logger logger = Logger.getLogger(ServiceManager.class.getName());

    // Singleton instance
    private static volatile ServiceManager instance;

    // Service registry: name -> Service
    private final Map<String, Service> services = new LinkedHashMap<>();

    // Scheduler - directly managed
    private final ScheduledThreadPoolExecutor scheduler;

    // Default thread pool size
    private static final int DEFAULT_POOL_SIZE = 2;

    // Private constructor
    private ServiceManager() {
        this.scheduler = new ScheduledThreadPoolExecutor(DEFAULT_POOL_SIZE);
        // Configure scheduler
        this.scheduler.setRemoveOnCancelPolicy(true);
        this.scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        this.scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    /**
     * Get ServiceManager singleton instance
     *
     * @return ServiceManager instance
     */
    public static ServiceManager getInstance() {
        if (instance == null) {
            synchronized (ServiceManager.class) {
                if (instance == null) {
                    instance = new ServiceManager();
                }
            }
        }
        return instance;
    }

    /**
     * Load all services using ServiceLoader
     * <p>
     * Automatically discover service implementations through Java module system
     * or META-INF/services.
     */
    public void loadServices() {
        logger.info("Loading services...");
        ServiceLoader<Service> loader = ServiceLoader.load(Service.class);

        for (Service service : loader) {
            registerService(service);
            logger.info("Loaded service: " + service.getName() + " - " + service.getDescription());
        }

        logger.info("Service loading completed, total " + services.size() + " services");
    }

    /**
     * Register service to manager
     * <p>
     * Manually register service implementation, used for services requiring constructor parameters (e.g., Index).
     *
     * @param service service to register
     */
    public void registerService(Service service) {
        if (services.containsKey(service.getName())) {
            logger.warning("Service " + service.getName() + " already exists, will be overwritten");
        }
        services.put(service.getName(), service);
        logger.info("Manually registered service: " + service.getName() + " - " + service.getDescription());
    }

    /**
     * Start all services
     * <p>
     * Start all loaded services sequentially.
     */
    public void startAll() {
        logger.info("Starting all services...");
        for (Service service : services.values()) {
            try {
                service.start(scheduler);
                logger.info("Service " + service.getName() + " started");
            } catch (Exception e) {
                logger.severe("Failed to start service " + service.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Stop all services and shutdown scheduler
     * <p>
     * Stop all services first, then gracefully shutdown the scheduler.
     */
    public void shutdown() {
        logger.info("Stopping all services...");

        // Stop all services first
        for (Service service : services.values()) {
            try {
                service.stop();
                logger.info("Service " + service.getName() + " stopped");
            } catch (Exception e) {
                logger.severe("Failed to stop service " + service.getName() + ": " + e.getMessage());
            }
        }

        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warning("Scheduler forced shutdown");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Service manager shutdown completed");
    }

    /**
     * systemctl status style - display all service status
     * <p>
     * Print name, state, and description of all services.
     */
    public void listStatus() {
        System.out.println("\n=== Service Status ===");
        System.out.printf("%-20s %-15s %-30s%n", "Name", "State", "Description");
        System.out.println("────────────────────────────────────────────────────────────────");

        for (Service service : services.values()) {
            String stateStr = String.format("%-15s", service.getState());
            String statusStr = service.isRunning() ? "✓" : "✗";
            System.out.printf("%-20s %-15s %s %s%n",
                service.getName(),
                stateStr,
                statusStr,
                service.getDescription());
        }
        System.out.println("────────────────────────────────────────────────────────────────");
    }

    /**
     * Get service by name
     *
     * @param name service name
     * @return Optional containing the service, or empty if not found
     */
    public Optional<Service> getService(String name) {
        return Optional.ofNullable(services.get(name));
    }

    /**
     * Find service by type
     * <p>
     * Find the first service matching the specified type in registration order.
     * Uses type-safe checking and casting.
     *
     * @param <T> service type
     * @param type service type to find (Class object)
     * @return Optional containing the first matching service instance, or empty if not found
     * <p>
     * &#064;example
     * <pre>{@code
     * // Find DeviceManager service
     * serviceManager.findService(DeviceManager.class)
     *     .ifPresent(DeviceManager::pause);
     *
     * // Find Index service
     * serviceManager.findService(Index.class)
     *     .ifPresent(Index::save);
     *
     * // Find service with custom action
     * Optional<DeviceManager> deviceManager = serviceManager.findService(DeviceManager.class);
     * deviceManager.ifPresent(dm -> dm.pause());
     * }</pre>
     */
    public <T extends Service> Optional<T> findService(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        // Iterate through all registered services, find first matching in registration order
        for (Service service : services.values()) {
            // Use Class.isInstance() for type-safe checking
            if (type.isInstance(service)) {
                // Use Class.cast() for type-safe casting
                return Optional.of(type.cast(service));
            }
        }

        // Return empty if not found
        return Optional.empty();
    }




    /**
     * Get all services
     *
     * @return unmodifiable collection of all services
     */
    public Collection<Service> getAllServices() {
        return Collections.unmodifiableCollection(services.values());
    }

    /**
     * Check if all services are healthy
     * <p>
     * Equivalent to systemctl is-active check for all services
     *
     * @return true if all services are running
     */
    public boolean isSystemHealthy() {
        return services.values().stream()
            .allMatch(Service::isRunning);
    }

    /**
     * Get list of failed services
     *
     * @return all services in failed state
     */
    public List<Service> getFailedServices() {
        return services.values().stream()
            .filter(Service::isFailed)
            .toList();
    }

    /**
     * Get scheduler
     * <p>
     * For components that need access to the scheduler
     *
     * @return scheduler instance
     */
    public ScheduledThreadPoolExecutor getScheduler() {
        return scheduler;
    }
}
