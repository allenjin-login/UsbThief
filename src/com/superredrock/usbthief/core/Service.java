package com.superredrock.usbthief.core;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Service extends Thread implements Closeable {

    protected final Logger logger = LogManager.getLogger(getClass());

    protected volatile ServiceState state = ServiceState.STOPPED;
    protected volatile boolean running = false;
    protected volatile boolean paused = false;
    protected final ReentrantLock stateLock = new ReentrantLock();

    /**
     * Signalled by {@link #pause()}, {@link #resume()} and {@link #stopService()} so that the
     * run loop can be woken immediately instead of sleeping through its tick interval.
     */
    private final Condition tickSignal = stateLock.newCondition();

    public Service() {
        super();
        setDaemon(true);
        // getServiceName() is implemented by subclasses and may read a field that is not
        // assigned yet while this constructor runs; fall back to the default thread name
        // instead of failing construction with an NPE.
        String serviceName = null;
        try {
            serviceName = getServiceName();
        } catch (RuntimeException e) {
            // subclass state not initialised yet - keep the default thread name
        }
        if (serviceName != null) {
            setName(serviceName);
        }
    }

    @Override
    public final void run() {
        stateLock.lock();
        try {
            running = true;
            state = ServiceState.RUNNING;
        } finally {
            stateLock.unlock();
        }
        logger.info("{} service started", getServiceName());

        // Tick and the inter-tick wait must never run while holding stateLock: pause()/resume()/
        // stopService() take the same lock and would otherwise be starved by the loop.
        boolean tickFailed = false;
        while (true) {
            boolean shouldTick;
            stateLock.lock();
            try {
                if (!running || Thread.currentThread().isInterrupted()) {
                    break;
                }
                shouldTick = (state == ServiceState.RUNNING);
            } finally {
                stateLock.unlock();
            }

            if (shouldTick) {
                try {
                    tick();
                } catch (Throwable t) {
                    // A failed service must not keep ticking (no zombie loop), and the worker
                    // thread must not die while "state" still claims RUNNING.
                    logger.error("{} tick failed: {}", getServiceName(), t);
                    tickFailed = true;
                    break;
                }
            }

            stateLock.lock();
            try {
                if (!running || Thread.currentThread().isInterrupted()) {
                    break;
                }
                // Wait outside the critical section; pause/resume/stop signal us awake.
                tickSignal.await(getTickInterval(), getTickUnit());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                stateLock.unlock();
            }
        }

        stateLock.lock();
        try {
            running = false;
            if (tickFailed) {
                state = ServiceState.FAILED;
            } else if (state != ServiceState.FAILED) {
                state = ServiceState.STOPPED;
            }
        } finally {
            stateLock.unlock();
        }
        logger.info("{} service stopped", getServiceName());
    }

    @Override
    public void start() {
        stateLock.lock();
        try {
            if (state == ServiceState.RUNNING || state == ServiceState.STARTING) {
                logger.warn("{} service is already running", getServiceName());
                return;
            }

            if (isAlive()) {
                logger.warn("{} thread is already alive", getServiceName());
                return;
            }

            if (getState() == Thread.State.TERMINATED) {
                // A Service owns its single worker thread; a terminated worker cannot be restarted.
                logger.warn("{} thread already terminated, service cannot be restarted", getServiceName());
                state = ServiceState.FAILED;
                return;
            }

            state = ServiceState.STARTING;
            super.start();

        } catch (Exception e) {
            logger.error("{} start failed: {}", getServiceName(), e);
            state = ServiceState.FAILED;
        } finally {
            stateLock.unlock();
        }
    }

    public void stopService() {
        stateLock.lock();
        try {
            if (state == ServiceState.STOPPED) {
                return;
            }

            state = ServiceState.STOPPING;
            running = false;
            tickSignal.signalAll();
        } finally {
            stateLock.unlock();
        }

        try {
            interrupt();

            if (isAlive()) {
                try {
                    join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("{} stop interrupted while waiting for thread", getServiceName());
                }
            }

            cleanup();

            stateLock.lock();
            try {
                if (state != ServiceState.FAILED) {
                    state = ServiceState.STOPPED;
                }
            } finally {
                stateLock.unlock();
            }
        } catch (Exception e) {
            logger.error("{} stop failed: {}", getServiceName(), e);
            state = ServiceState.FAILED;
        }
    }

    public void pause() {
        stateLock.lock();
        try {
            if (state != ServiceState.RUNNING) {
                logger.warn("{} service is not running, cannot pause", getServiceName());
                return;
            }

            paused = true;
            state = ServiceState.PAUSED;
            tickSignal.signalAll();
            logger.info("{} service paused", getServiceName());

        } catch (Exception e) {
            logger.error("{} pause failed: {}", getServiceName(), e);
            state = ServiceState.FAILED;
        } finally {
            stateLock.unlock();
        }
    }

    public void resume() {
        stateLock.lock();
        try {
            if (state != ServiceState.PAUSED) {
                logger.warn("{} service is not paused, cannot resume", getServiceName());
                return;
            }

            paused = false;
            state = ServiceState.RUNNING;
            tickSignal.signalAll();
            logger.info("{} service resumed", getServiceName());

        } catch (Exception e) {
            logger.error("{} resume failed: {}", getServiceName(), e);
            state = ServiceState.FAILED;
        } finally {
            stateLock.unlock();
        }
    }

    public final ServiceState getServiceState() {
        return state;
    }

    public boolean isRunning() {
        return state == ServiceState.RUNNING;
    }

    public boolean isFailed() {
        return state == ServiceState.FAILED;
    }

    public String getStatus() {
        return String.format("%s[%s]", getServiceName(), state);
    }

    @Override
    public void close() {
        stopService();
    }

    protected abstract void tick();

    protected abstract long getTickInterval();

    protected abstract TimeUnit getTickUnit();

    public abstract String getServiceName();

    public abstract String getDescription();

    protected void cleanup() {
    }
}
