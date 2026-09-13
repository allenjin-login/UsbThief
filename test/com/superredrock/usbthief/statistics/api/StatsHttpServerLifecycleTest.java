package com.superredrock.usbthief.statistics.api;

import com.superredrock.usbthief.core.config.ConfigManager;
import com.superredrock.usbthief.core.config.configs.StatisticsApiConfig;
import com.superredrock.usbthief.statistics.MetricRegistry;
import com.superredrock.usbthief.statistics.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AR-10 (phase 1): the statistics HTTP API must only bind a socket when it is started
 * explicitly, never as a side effect of constructing the objects around it.
 *
 * <p>Before the change, merely touching {@code Statistics.getInstance()} - which happened
 * implicitly the moment {@code CopyTask} was class-loaded - constructed the HTTP server and
 * started it. These tests pin the new contract: construction binds nothing, {@code start()}
 * binds, {@code stop()} releases.
 */
class StatsHttpServerLifecycleTest {

    /** Construction must not open a socket and stop() before start() must be a no-op. */
    @Test
    @Timeout(20)
    void constructionBindsNothingAndStopBeforeStartIsSafe() {
        StatsHttpServer server = new StatsHttpServer();

        assertFalse(server.isRunning(), "a freshly constructed server must not be running");

        // Must not throw and must not change state - shutdown paths call stop() unconditionally.
        server.stop();
        assertFalse(server.isRunning(), "stopping a server that was never started must be a no-op");
    }

    /** With the API disabled (the default), start() must not bind anything either. */
    @Test
    @Timeout(20)
    void startDoesNothingWhenApiIsDisabled() {
        ConfigManager config = ConfigManager.getInstance();
        boolean previousEnabled = config.get(StatisticsApiConfig.STATS_API_ENABLED);
        try {
            config.set(StatisticsApiConfig.STATS_API_ENABLED, false);

            StatsHttpServer server = new StatsHttpServer();
            server.start(new MetricRegistry());

            assertFalse(server.isRunning(), "a disabled API must not report as running");
        } finally {
            config.set(StatisticsApiConfig.STATS_API_ENABLED, previousEnabled);
        }
    }

    /**
     * The regression that motivated the refactor: obtaining the {@code Statistics} singleton must
     * not bind the configured port. Binding happens only in {@code Statistics.start()}, which the
     * assembly point calls explicitly.
     */
    @Test
    @Timeout(20)
    void obtainingStatisticsDoesNotBindTheConfiguredPort() throws Exception {
        ConfigManager config = ConfigManager.getInstance();
        boolean previousEnabled = config.get(StatisticsApiConfig.STATS_API_ENABLED);
        int previousPort = config.get(StatisticsApiConfig.STATS_API_PORT);
        int port = findFreePort();
        try {
            config.set(StatisticsApiConfig.STATS_API_ENABLED, true);
            config.set(StatisticsApiConfig.STATS_API_PORT, port);

            Statistics statistics = Statistics.getInstance();
            assertNotNull(statistics);

            assertFalse(listeningOn(port),
                    "Statistics must not bind the API port before start() is called");
        } finally {
            config.set(StatisticsApiConfig.STATS_API_ENABLED, previousEnabled);
            config.set(StatisticsApiConfig.STATS_API_PORT, previousPort);
        }
    }

    /** start() binds the configured port; stop() releases it again. */
    @Test
    @Timeout(20)
    void startBindsTheConfiguredPortAndStopReleasesIt() throws Exception {
        ConfigManager config = ConfigManager.getInstance();
        boolean previousEnabled = config.get(StatisticsApiConfig.STATS_API_ENABLED);
        int previousPort = config.get(StatisticsApiConfig.STATS_API_PORT);
        int port = findFreePort();
        StatsHttpServer server = new StatsHttpServer();
        try {
            config.set(StatisticsApiConfig.STATS_API_ENABLED, true);
            config.set(StatisticsApiConfig.STATS_API_PORT, port);

            assertFalse(listeningOn(port), "no port may be bound before start()");

            server.start(new MetricRegistry());

            assertTrue(server.isRunning(), "start() must bring the API up");
            assertTrue(listeningOn(port), "start() must bind the configured port");

            server.stop();

            assertFalse(server.isRunning(), "stop() must mark the API as down");
            awaitNotListening(port);
            assertFalse(listeningOn(port), "stop() must release the port");
        } finally {
            server.stop();
            config.set(StatisticsApiConfig.STATS_API_ENABLED, previousEnabled);
            config.set(StatisticsApiConfig.STATS_API_PORT, previousPort);
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static boolean listeningOn(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void awaitNotListening(int port) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (listeningOn(port) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }
}
