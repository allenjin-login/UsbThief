package com.superredrock.usbthief.statistics.api;

import com.sun.net.httpserver.HttpExchange;
import com.superredrock.usbthief.statistics.MetricRegistry;
import com.superredrock.usbthief.statistics.collector.MetricSnapshot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

final class StatsEventHandler {
    private static final Logger logger = LogManager.getLogger(StatsEventHandler.class);
    private final MetricRegistry registry;
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();

    StatsEventHandler(MetricRegistry registry) {
        this.registry = registry;
    }

    void handleStats(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("GET".equals(method)) {
                // /api/stats or /api/stats/
                if (path.equals("/api/stats") || path.equals("/api/stats/")) {
                    sendJson(exchange, 200, toJson(registry.getAllSnapshots()));
                } else {
                    // /api/stats/{metricId}
                    String metricId = path.substring("/api/stats/".length());
                    MetricSnapshot snapshot = registry.getSnapshot(metricId);
                    if (snapshot != null) {
                        sendJson(exchange, 200, snapshotToJson(snapshot));
                    } else {
                        sendJson(exchange, 404, "{\"error\":\"metric not found\"}");
                    }
                }
            } else if ("POST".equals(method) && path.endsWith("/reset")) {
                registry.resetAll();
                sendJson(exchange, 200, "{\"status\":\"reset\"}");
            } else {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
            }
        } catch (Exception e) {
            logger.warn("Error handling stats request: {}", e.getMessage());
            sendJson(exchange, 500, "{\"error\":\"internal error\"}");
        }
    }

    void handleStream(HttpExchange exchange) throws IOException {
        var responseHeaders = exchange.getResponseHeaders();
        responseHeaders.set("Content-Type", "text/event-stream");
        responseHeaders.set("Cache-Control", "no-cache");
        responseHeaders.set("Connection", "keep-alive");
        responseHeaders.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);

        SseClient client = new SseClient(exchange);
        sseClients.add(client);

        // Send initial snapshot
        client.send(toJson(registry.getAllSnapshots()));
    }

    void pushUpdates() {
        if (sseClients.isEmpty()) return;
        String json = toJson(registry.getAllSnapshots());
        for (var client : sseClients) {
            if (!client.send(json)) {
                sseClients.remove(client);
            }
        }
    }

    void closeAll() {
        for (var client : sseClients) {
            client.close();
        }
        sseClients.clear();
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // --- JSON building ---

    private String toJson(Map<String, MetricSnapshot> snapshots) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : snapshots.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(entry.getKey())).append("\":");
            sb.append(snapshotToJson(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    private String snapshotToJson(MetricSnapshot s) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"longValue\":").append(s.longValue());
        sb.append(",\"doubleValue\":").append(s.doubleValue());
        if (!s.details().isEmpty()) {
            sb.append(",\"details\":{");
            boolean first = true;
            for (var e : s.details().entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(e.getKey())).append("\":");
                sb.append(valueToJson(e.getValue()));
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private String valueToJson(Object v) {
        if (v instanceof Number) return v.toString();
        if (v instanceof Boolean) return v.toString();
        if (v instanceof String) return '"' + escape((String) v) + '"';
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(String.valueOf(e.getKey()))).append("\":");
                sb.append(valueToJson(e.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        return '"' + escape(v.toString()) + '"';
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // --- SSE client ---

    private static class SseClient {
        private final HttpExchange exchange;
        private final OutputStream out;
        private volatile boolean closed;

        SseClient(HttpExchange exchange) {
            this.exchange = exchange;
            this.out = exchange.getResponseBody();
        }

        boolean send(String json) {
            if (closed) return false;
            try {
                out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                return true;
            } catch (IOException e) {
                close();
                return false;
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            try { out.close(); } catch (IOException ignored) {}
            try { exchange.close(); } catch (Exception ignored) {}
        }
    }
}
