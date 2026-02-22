package com.superredrock.usbthief.core;

import java.util.*;
import java.util.logging.Logger;

/**
 * Service Manager
 * <p>
 * Responsible for loading, managing, and coordinating all services.
 * Uses ServiceLoader to automatically discover service implementations.
 * Services run as separate threads with tick-based execution.
 * <p>
 * Provides systemctl-like batch management functionality: start, stop, and status queries.
 * <p>
 * Uses singleton pattern, consistent with QueueManager and ConfigManager.
 */
public class ServiceManager {

    protected static final Logger logger = Logger.getLogger(ServiceManager.class.getName());

    private static volatile ServiceManager instance;

    private final Map<String, Service> services = new LinkedHashMap<>();

    private ServiceManager() {
    }

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


    public void loadServices() {
        logger.info("Loading services...");
        ServiceLoader<Service> loader = ServiceLoader.load(Service.class);

        for (Service service : loader) {
            registerService(service);
            logger.info("Loaded service: " + service.getServiceName() + " - " + service.getDescription());
        }

        logger.info("Service loading completed, total " + services.size() + " services");
    }

    public void registerService(Service service) {
        if (services.containsKey(service.getServiceName())) {
            logger.warning("Service " + service.getServiceName() + " already exists, will be overwritten");
        }
        services.put(service.getServiceName(), service);
        logger.info("Manually registered service: " + service.getServiceName() + " - " + service.getDescription());
    }

    public void startAll() {
        logger.info("Starting all services...");
        for (Service service : services.values()) {
            try {
                service.start();
                logger.info("Service " + service.getServiceName() + " started");
            } catch (Exception e) {
                logger.severe("Failed to start service " + service.getServiceName() + ": " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        logger.info("Stopping all services...");

        for (Service service : services.values()) {
            try {
                service.stopService();
                logger.info("Service " + service.getServiceName() + " stopped");
            } catch (Exception e) {
                logger.severe("Failed to stop service " + service.getServiceName() + ": " + e.getMessage());
            }
        }

        logger.info("Service manager shutdown completed");
    }

    public void listStatus() {
        System.out.println("\n=== Service Status ===");
        System.out.printf("%-20s %-15s %-30s%n", "Name", "State", "Description");
        System.out.println("────────────────────────────────────────────────────────────────");

        for (Service service : services.values()) {
            String stateStr = String.format("%-15s", service.getServiceState());
            String statusStr = service.isRunning() ? "✓" : "✗";
            System.out.printf("%-20s %-15s %s %s%n",
                service.getServiceName(),
                stateStr,
                statusStr,
                service.getDescription());
        }
        System.out.println("────────────────────────────────────────────────────────────────");
    }

    public Optional<Service> getService(String name) {
        return Optional.ofNullable(services.get(name));
    }

    public <T extends Service> Optional<T> findService(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        for (Service service : services.values()) {
            if (type.isInstance(service)) {
                return Optional.of(type.cast(service));
            }
        }
        return Optional.empty();
    }

    public <T extends  Service> T getInstance(Class<T> tClass){
        Optional<T>  optional = this.findService(tClass);

        if (optional.isPresent()){
            return optional.get();
        }else {
            try {
                T NewService = tClass.newInstance();
                this.registerService(NewService);
                return NewService;
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public Collection<Service> getAllServices() {
        return Collections.unmodifiableCollection(services.values());
    }

    public boolean isSystemHealthy() {
        return services.values().stream()
            .allMatch(Service::isRunning);
    }

    public List<Service> getFailedServices() {
        return services.values().stream()
            .filter(Service::isFailed)
            .toList();
    }
}
