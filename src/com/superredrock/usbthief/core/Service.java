package com.superredrock.usbthief.core;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public abstract class Service extends Thread implements Closeable {

    protected final Logger logger = Logger.getLogger(getClass().getName());

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
        logger.info(getServiceName() + " service started");

        while (running && !Thread.currentThread().isInterrupted()) {
            stateLock.lock();
            try {
                if (!paused) {
                    tick();
                }
                TimeUnit.MILLISECONDS.sleep(getTickIntervalMs());
            } catch (InterruptedException e) {
                if (running) {
                    logger.severe(getServiceName() + " interrupted unexpectedly");
                }
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.severe(getServiceName() + " tick failed: " + e.getMessage());
                state = ServiceState.FAILED;
            }finally {
                stateLock.unlock();
            }
        }

        state = ServiceState.STOPPED;
        logger.info(getServiceName() + " service stopped");
    }

    @Override
    public void start() {
        stateLock.lock();
        try {
            if (state == ServiceState.RUNNING || state == ServiceState.STARTING) {
                logger.warning(getServiceName() + " service is already running");
                return;
            }

            if (isAlive()) {
                logger.warning(getServiceName() + " thread is already alive");
                return;
            }

            state = ServiceState.STARTING;
            super.start();

        } catch (Exception e) {
            logger.severe(getServiceName() + " start failed: " + e.getMessage());
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
                    logger.warning(getServiceName() + " stop interrupted while waiting for thread");
                }
            }

            cleanup();

            state = ServiceState.STOPPED;
            logger.info(getServiceName() + " service stopped");

        } catch (Exception e) {
            logger.severe(getServiceName() + " stop failed: " + e.getMessage());
            state = ServiceState.FAILED;
        }
    }

    public void pause() {
        stateLock.lock();
        try {
            if (state != ServiceState.RUNNING) {
                logger.warning(getServiceName() + " service is not running, cannot pause");
                return;
            }

            paused = true;
            state = ServiceState.PAUSED;
            logger.info(getServiceName() + " service paused");

        } catch (Exception e) {
            logger.severe(getServiceName() + " pause failed: " + e.getMessage());
            state = ServiceState.FAILED;
        } finally {
            stateLock.unlock();
        }
    }

    public void resume() {
        stateLock.lock();
        try {
            if (state != ServiceState.PAUSED) {
                logger.warning(getServiceName() + " service is not paused, cannot resume");
                return;
            }

            paused = false;
            state = ServiceState.RUNNING;
            logger.info(getServiceName() + " service resumed");

        } catch (Exception e) {
            logger.severe(getServiceName() + " resume failed: " + e.getMessage());
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

    protected abstract long getTickIntervalMs();

    public abstract String getServiceName();

    public abstract String getDescription();

    protected void cleanup() {
    }
}
