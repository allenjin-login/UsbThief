package com.superredrock.usbthief.core;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Service extends Thread implements Closeable {

    protected final Logger logger = LogManager.getLogger(getClass());

    protected volatile ServiceState state = ServiceState.STOPPED;
    protected volatile boolean running = false;
    protected volatile boolean paused = false;
    protected final ReentrantLock stateLock = new ReentrantLock();

    public Service() {
        super();
        setDaemon(true);
        setName(getServiceName());
    }

    @Override
    public final void run() {
        running = true;
        state = ServiceState.RUNNING;
        logger.info("{} service started", getServiceName());


        while (running && !Thread.currentThread().isInterrupted()) {
            stateLock.lock();
            try {
                if (!paused) {
                    tick();
                }
                getTickUnit().sleep(getTickInterval());
            } catch (InterruptedException e) {
                if (running) {
                    logger.error("{} interrupted unexpectedly", getServiceName());
                }
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("{} tick failed: {}", getServiceName(), e);
                state = ServiceState.FAILED;
            }finally {
                stateLock.unlock();
            }
        }

        state = ServiceState.STOPPED;
        logger.info(" service stopped{}", getServiceName());
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
        if (state == ServiceState.STOPPED) {
            return;
        }

        state = ServiceState.STOPPING;

        try {
            running = false;
            interrupt();

            if (isAlive()) {
                try {
                    join(5000);
                } catch (InterruptedException e) {
                    logger.warn("{} stop interrupted while waiting for thread", getServiceName());
                }
            }

            cleanup();

            state = ServiceState.STOPPED;
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
            logger.info("{} service paused", getServiceName());

        } catch (Exception e) {
            logger.error("{} pause failed: {}", getServiceName(), e);
            state = ServiceState.FAILED;
        } finally {
            stateLock.unlock();
        }
    }

    public void resumeService() {
        stateLock.lock();
        try {
            if (state != ServiceState.PAUSED) {
                logger.warn("{} service is not paused, cannot resume", getServiceName());
                return;
            }

            paused = false;
            state = ServiceState.RUNNING;
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
