package com.superredrock.usbthief.core;

import java.io.Closeable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

/**
 * 服务抽象基类
 * <p>
 * 统一管理服务的生命周期、状态转换和任务调度。
 * 子类只需实现调度逻辑和具体的 tick 任务。
 * <p>
 * 自动管理的功能：
 * - 状态转换（STOPPED → STARTING → RUNNING → STOPPING → STOPPED）
 * - ScheduledFuture 的创建和取消
 * - 错误状态处理
 * <p>
 * 子类需要实现：
 * - {@link #scheduleTask(ScheduledThreadPoolExecutor)} - 定义如何调度任务
 * - {@link #run()} - 实现具体的 tick 逻辑
 * - {@link #getName()} - 返回服务名称
 * - {@link #getDescription()} - 返回服务描述
 * - {@link #cleanup()} - （可选）清理资源
 */
public abstract class Service implements Runnable , Closeable {

    protected final Logger logger = Logger.getLogger(getClass().getName());

    // 服务状态管理
    protected volatile ServiceState state = ServiceState.STOPPED;

    // 调度任务
    protected ScheduledFuture<?> scheduledTask;

    // ========== 生命周期控制方法 ==========

    /**
     * 启动服务
     * <p>
     * 自动管理状态转换，创建调度任务。
     *
     * @param scheduler 调度器
     */
    public void start(ScheduledThreadPoolExecutor scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler cannot be null");
        }

        if (state == ServiceState.RUNNING) {
            logger.warning(getName() + " 服务已在运行");
            return;
        }

        state = ServiceState.STARTING;

        try {
            // 取消旧任务
            if (scheduledTask != null && !scheduledTask.isDone()) {
                scheduledTask.cancel(false);
            }

            // 子类定义调度逻辑
            scheduledTask = scheduleTask(scheduler);
            state = ServiceState.RUNNING;
            logger.info(getName() + " 服务已启动");

        } catch (Exception e) {
            logger.severe(getName() + " 启动失败: " + e.getMessage());
            state = ServiceState.FAILED;
        }
    }

    /**
     * 停止服务
     * <p>
     * 取消调度任务，调用清理钩子，重置状态。
     */
    public void stop() {
        if (state == ServiceState.STOPPED) {
            return;
        }

        state = ServiceState.STOPPING;

        try {
            // 取消调度任务
            if (scheduledTask != null) {
                scheduledTask.cancel(true);
                scheduledTask = null;
            }

            // 调用清理钩子
            cleanup();

            state = ServiceState.STOPPED;
            logger.info(getName() + " 服务已停止");

        } catch (Exception e) {
            logger.severe(getName() + " 停止失败: " + e.getMessage());
            state = ServiceState.FAILED;
        }
    }

    /**
     * 暂停服务
     * <p>
     * 暂停定期执行，但不释放资源。
     */
    public void pause() {
        if (state != ServiceState.RUNNING) {
            logger.warning(getName() + " 服务未运行，无法暂停");
            return;
        }

        try {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
                scheduledTask = null;
            }

            state = ServiceState.PAUSED;
            logger.info(getName() + " 服务已暂停");

        } catch (Exception e) {
            logger.severe(getName() + " 暂停失败: " + e.getMessage());
            state = ServiceState.FAILED;
        }
    }

    /**
     * 恢复服务
     * <p>
     * 从暂停状态恢复执行。需要传入调度器重新创建任务。
     *
     * @param scheduler 调度器
     */
    public void resume(ScheduledThreadPoolExecutor scheduler) {
        if (state != ServiceState.PAUSED) {
            logger.warning(getName() + " 服务未暂停，无法恢复");
            return;
        }

        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler cannot be null");
        }

        try {
            scheduledTask = scheduleTask(scheduler);
            state = ServiceState.RUNNING;
            logger.info(getName() + " 服务已恢复");

        } catch (Exception e) {
            logger.severe(getName() + " 恢复失败: " + e.getMessage());
            state = ServiceState.FAILED;
        }
    }

    // ========== 状态查询方法 ==========

    /**
     * 获取当前服务状态
     *
     * @return 当前状态
     */
    public ServiceState getState() {
        return state;
    }

    /**
     * 检查服务是否正在运行
     *
     * @return 如果服务正在运行，返回 true
     */
    public boolean isRunning() {
        return state == ServiceState.RUNNING;
    }

    /**
     * 检查服务是否处于失败状态
     *
     * @return 如果服务失败，返回 true
     */
    public boolean isFailed() {
        return state == ServiceState.FAILED;
    }

    /**
     * 获取服务的详细状态描述
     * <p>
     * 子类可以覆盖此方法提供更多信息。
     *
     * @return 状态描述字符串
     */
    public String getStatus() {
        return String.format("%s[%s]", getName(), state);
    }

    public void close(){
        stop();
    }

    // ========== 子类必须实现的方法 ==========

    /**
     * 定义任务调度逻辑
     * <p>
     * 子类使用 scheduler.scheduleAtFixedRate() 或 scheduler.scheduleWithFixedDelay()
     * 创建调度任务，并返回 ScheduledFuture。
     *
     * @param scheduler 调度器
     * @return 调度任务的 Future
     */
    protected abstract ScheduledFuture<?> scheduleTask(ScheduledThreadPoolExecutor scheduler);

    /**
     * 获取服务名称
     * <p>
     * 服务名称必须唯一，用于服务注册和识别。
     *
     * @return 服务名称
     */
    public abstract String getName();

    /**
     * 获取服务描述
     *
     * @return 服务描述
     */
    public abstract String getDescription();

    /**
     * 执行一次 tick 任务
     * <p>
     * 由调度器定期调用。子类实现具体的业务逻辑。
     */
    public abstract void run();

    // ========== 子类可选覆盖的钩子方法 ==========

    /**
     * 清理资源钩子
     * <p>
     * 在服务停止时调用。子类可以覆盖此方法释放资源。
     * 默认实现为空。
     */
    protected void cleanup() {
        // 默认不做任何事，子类可以选择覆盖
    }


}
