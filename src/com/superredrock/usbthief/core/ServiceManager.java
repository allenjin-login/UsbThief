package com.superredrock.usbthief.core;

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 服务管理器
 * <p>
 * 负责加载、管理和协调所有服务。使用 ServiceLoader 自动发现服务实现，
 * 内部管理 ScheduledThreadPoolExecutor 进行任务调度。
 * <p>
 * 提供类似 systemctl 的批量管理功能：启动、停止、状态查询。
 * <p>
 * 使用单例模式，与 QueueManager 和 ConfigManager 保持一致。
 */
public class ServiceManager {

    protected static final Logger logger = Logger.getLogger(ServiceManager.class.getName());

    // 单例实例
    private static volatile ServiceManager instance;

    // 服务注册表：name -> AbstractService
    private final Map<String, Service> services = new LinkedHashMap<>();

    // 调度器 - 直接管理
    private final ScheduledThreadPoolExecutor scheduler;

    // 默认线程池大小
    private static final int DEFAULT_POOL_SIZE = 2;

    // 私有构造器
    private ServiceManager() {
        this.scheduler = new ScheduledThreadPoolExecutor(DEFAULT_POOL_SIZE);
        // 配置调度器
        this.scheduler.setRemoveOnCancelPolicy(true);
        this.scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        this.scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    /**
     * 获取 ServiceManager 单例
     *
     * @return ServiceManager 实例
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
     * 使用 ServiceLoader 加载所有服务
     * <p>
     * 通过 Java 模块系统或 META-INF/services 自动发现服务实现。
     */
    public void loadServices() {
        logger.info("正在加载服务...");
        ServiceLoader<Service> loader = ServiceLoader.load(Service.class);

        for (Service service : loader) {
            registerService(service);
            logger.info("已加载服务: " + service.getName() + " - " + service.getDescription());
        }

        logger.info("服务加载完成，共 " + services.size() + " 个服务");
    }

    /**
     * 注册服务到管理器
     * <p>
     * 手动注册服务实现，用于需要构造参数的服务（如 Index）。
     *
     * @param service 要注册的服务
     */
    public void registerService(Service service) {
        if (services.containsKey(service.getName())) {
            logger.warning("服务 " + service.getName() + " 已存在，将被覆盖");
        }
        services.put(service.getName(), service);
        logger.info("手动注册服务: " + service.getName() + " - " + service.getDescription());
    }

    /**
     * 启动所有服务
     * <p>
     * 依次启动所有已加载的服务。
     */
    public void startAll() {
        logger.info("正在启动所有服务...");
        for (Service service : services.values()) {
            try {
                service.start(scheduler);
                logger.info("服务 " + service.getName() + " 已启动");
            } catch (Exception e) {
                logger.severe("启动服务 " + service.getName() + " 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 停止所有服务并关闭调度器
     * <p>
     * 先停止所有服务，然后优雅地关闭调度器。
     */
    public void shutdown() {
        logger.info("正在停止所有服务...");

        // 先停止所有服务
        for (Service service : services.values()) {
            try {
                service.stop();
                logger.info("服务 " + service.getName() + " 已停止");
            } catch (Exception e) {
                logger.severe("停止服务 " + service.getName() + " 失败: " + e.getMessage());
            }
        }

        // 关闭调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warning("调度器强制关闭");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("服务管理器已关闭");
    }

    /**
     * systemctl status 风格 - 显示所有服务状态
     * <p>
     * 打印所有服务的名称、状态和描述。
     */
    public void listStatus() {
        System.out.println("\n=== 服务状态 ===");
        System.out.printf("%-20s %-15s %-30s%n", "名称", "状态", "描述");
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
     * 根据名称获取服务
     *
     * @param name 服务名称
     * @return 包含服务的 Optional，如果不存在则返回空
     */
    public Optional<Service> getService(String name) {
        return Optional.ofNullable(services.get(name));
    }

    /**
     * 根据类型查找服务
     * <p>
     * 按注册顺序查找第一个匹配指定类型的服务。
     * 使用类型安全的检查和转换。
     *
     * @param <T> 服务类型
     * @param type 要查找的服务类型（Class 对象）
     * @return 第一个匹配的服务实例，找不到返回 null
     * <p>
     * &#064;example
     * <pre>{@code
     * // 查找 DeviceManager 服务
     * DeviceManager deviceManager = serviceManager.findService(DeviceManager.class);
     * if (deviceManager != null) {
     *     deviceManager.pause();
     * }
     *
     * // 查找 Index 服务
     * Index index = serviceManager.findService(Index.class);
     * if (index != null) {
     *     index.save();
     * }
     * }</pre>
     */
    public <T extends Service> T findService(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        // 遍历所有注册的服务，按注册顺序查找第一个匹配的
        for (Service service : services.values()) {
            // 使用 Class.isInstance() 进行类型安全检查
            if (type.isInstance(service)) {
                // 使用 Class.cast() 进行类型安全转换
                return type.cast(service);
            }
        }

        // 找不到返回 null
        return null;
    }




    /**
     * 获取所有服务
     *
     * @return 所有服务的不可修改集合
     */
    public Collection<Service> getAllServices() {
        return Collections.unmodifiableCollection(services.values());
    }

    /**
     * 检查所有服务是否健康
     * <p>
     * 相当于 systemctl is-active 检查所有服务
     *
     * @return 如果所有服务都在运行，返回 true
     */
    public boolean isSystemHealthy() {
        return services.values().stream()
            .allMatch(Service::isRunning);
    }

    /**
     * 获取失败的服务列表
     *
     * @return 所有处于失败状态的服务
     */
    public List<Service> getFailedServices() {
        return services.values().stream()
            .filter(Service::isFailed)
            .toList();
    }

    /**
     * 获取调度器
     * <p>
     * 供需要访问调度器的组件使用
     *
     * @return 调度器实例
     */
    public ScheduledThreadPoolExecutor getScheduler() {
        return scheduler;
    }
}
