package com.superredrock.usbthief.statistics.api;

import com.sun.net.httpserver.HttpServer;
import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.StatisticsApiConfig;
import com.superredrock.usbthief.statistics.MetricRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class StatsHttpServer {
    private static final Logger logger = LogManager.getLogger(StatsHttpServer.class);

    private HttpServer server;
    private ScheduledExecutorService scheduler;
    private StatsEventHandler handler;
    private volatile boolean running;

    public void start(MetricRegistry registry) {
        if (!ConfigManager.getInstance().get(StatisticsApiConfig.STATS_API_ENABLED)) {
            logger.info("Stats HTTP API disabled");
            return;
        }

        int port = ConfigManager.getInstance().get(StatisticsApiConfig.STATS_API_PORT);
        handler = new StatsEventHandler(registry);

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/stats/stream", handler::handleStream);
            server.createContext("/api/stats", handler::handleStats);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();

            // Push SSE updates every 2 seconds
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "stats-sse-pusher");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(handler::pushUpdates, 2, 2, TimeUnit.SECONDS);

            running = true;
            logger.info("Stats HTTP API started on port {}", port);
        } catch (IOException e) {
            logger.warn("Failed to start stats HTTP API on port {}: {}", port, e.getMessage());
        }
    }

    public void stop() {
        if (!running) return;
        running = false;

        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (handler != null) {
            handler.closeAll();
        }
        if (server != null) {
            server.stop(1);
        }
        logger.info("Stats HTTP API stopped");
    }

    public boolean isRunning() {
        return running;
    }
}
