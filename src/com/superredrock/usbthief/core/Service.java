package com.superredrock.usbthief.core;

import java.io.Closeable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * Service abstract base class
 * <p>
 * Uniformly manages service lifecycle, state transitions, and task scheduling.
 * Subclasses only need to implement scheduling logic and specific tick tasks.
 * <p>
 * Automatically managed features:
 * - State transitions (STOPPED → STARTING → RUNNING → STOPPING → STOPPED)
 * - Creation and cancellation of ScheduledFuture
 * - Error state handling
 * <p>
 * Subclasses need to implement:
 * - {@link #scheduleTask(ScheduledThreadPoolExecutor)} - define how to schedule tasks
 * - {@link #run()} - implement specific tick logic
 * - {@link #getName()} - return service name
 * - {@link #getDescription()} - return service description
 * - {@link #cleanup()} - (optional) clean up resources
 */
public abstract class Service implements Runnable , Closeable {

    protected final Logger logger = Logger.getLogger(getClass().getName());

    // Service state management
    protected volatile ServiceState state = ServiceState.STOPPED;

    // Scheduled task
    protected ScheduledFuture<?> scheduledTask;

    // Lock for state transitions
    protected final ReentrantLock stateLock = new ReentrantLock();

    // ========== Lifecycle control methods ==========

    /**
     * Start service
     * <p>
     * Automatically manages state transitions and creates scheduled tasks.
     *
     * @param scheduler scheduler
     */
    public void start(ScheduledThreadPoolExecutor scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler cannot be null");
        }

        stateLock.lock();
        try {
            if (state == ServiceState.RUNNING) {
                logger.warning(getName() + " service is already running");
                return;
            }

            state = ServiceState.STARTING;

            try {
                if (scheduledTask != null && !scheduledTask.isDone()) {
                    scheduledTask.cancel(false);
                }

                scheduledTask = scheduleTask(scheduler);
                state = ServiceState.RUNNING;
                logger.info(getName() + " service started");

            } catch (Exception e) {
                logger.severe(getName() + " start failed: " + e.getMessage());
                state = ServiceState.FAILED;
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Stop service
     * <p>
     * Cancel scheduled task, call cleanup hook, and reset state.
     */
    public void stop() {
        stateLock.lock();
        try {
            if (state == ServiceState.STOPPED) {
                return;
            }

            state = ServiceState.STOPPING;

            try {
                if (scheduledTask != null) {
                    scheduledTask.cancel(true);
                    scheduledTask = null;
                }

                cleanup();

                state = ServiceState.STOPPED;
                logger.info(getName() + " service stopped");

            } catch (Exception e) {
                logger.severe(getName() + " stop failed: " + e.getMessage());
                state = ServiceState.FAILED;
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Pause service
     * <p>
     * Pause periodic execution without releasing resources.
     */
    public void pause() {
        stateLock.lock();
        try {
            if (state != ServiceState.RUNNING) {
                logger.warning(getName() + " service is not running, cannot pause");
                return;
            }

            try {
                if (scheduledTask != null) {
                    scheduledTask.cancel(false);
                    scheduledTask = null;
                }

                state = ServiceState.PAUSED;
                logger.info(getName() + " service paused");

            } catch (Exception e) {
                logger.severe(getName() + " pause failed: " + e.getMessage());
                state = ServiceState.FAILED;
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Resume service
     * <p>
     * Resume execution from paused state. Requires scheduler to recreate task.
     *
     * @param scheduler scheduler
     */
    public void resume(ScheduledThreadPoolExecutor scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler cannot be null");
        }

        if (state != ServiceState.PAUSED) {
            logger.warning(getName() + " service is not paused, cannot resume");
            return;
        }

        stateLock.lock();
        try {
            scheduledTask = scheduleTask(scheduler);
            state = ServiceState.RUNNING;
            logger.info(getName() + " service resumed");
        } catch (Exception e) {
            logger.severe(getName() + " resume failed: " + e.getMessage());
            state = ServiceState.FAILED;
        } finally {
            stateLock.unlock();
        }
    }

    // ========== State query methods ==========

    /**
     * Get current service state
     *
     * @return current state
     */
    public ServiceState getState() {
        return state;
    }

    /**
     * Check if service is running
     *
     * @return true if service is running
     */
    public boolean isRunning() {
        return state == ServiceState.RUNNING;
    }

    /**
     * Check if service is in failed state
     *
     * @return true if service failed
     */
    public boolean isFailed() {
        return state == ServiceState.FAILED;
    }

    /**
     * Get detailed service status description
     * <p>
     * Subclasses can override this method to provide more information.
     *
     * @return status description string
     */
    public String getStatus() {
        return String.format("%s[%s]", getName(), state);
    }

    public void close(){
        stop();
    }

    // ========== Methods subclasses must implement ==========

    /**
     * Define task scheduling logic
     * <p>
     * Subclasses use scheduler.scheduleAtFixedRate() or scheduler.scheduleWithFixedDelay()
     * to create scheduled tasks and return ScheduledFuture.
     *
     * @param scheduler scheduler
     * @return Future of scheduled task
     */
    protected abstract ScheduledFuture<?> scheduleTask(ScheduledThreadPoolExecutor scheduler);

    /**
     * Get service name
     * <p>
     * Service name must be unique, used for service registration and identification.
     *
     * @return service name
     */
    public abstract String getName();

    /**
     * Get service description
     *
     * @return service description
     */
    public abstract String getDescription();

    /**
     * Execute one tick task
     * <p>
     * Called periodically by scheduler. Subclasses implement specific business logic.
     */
    public abstract void run();

    // ========== Optional hook methods subclasses can override ==========

    /**
     * Cleanup resource hook
     * <p>
     * Called when service stops. Subclasses can override this method to release resources.
     * Default implementation does nothing.
     */
    protected void cleanup() {
        // Default does nothing, subclasses can choose to override
    }


}
