package com.cx.airouter.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 以 JDK 內建 HttpServer 實作的測試替身，可模擬 OpenAI API 與既有 Agent API
 * （回應內容、HTTP 狀態、延遲），並記錄收到的請求供驗證。
 */
public final class StubHttpServer implements AutoCloseable {

    public record Recorded(String method, String path, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst().orElse(null);
        }
    }

    public record StubResponse(int status, String body, long delayMs) {
        public static StubResponse json(String body) {
            return new StubResponse(200, body, 0);
        }

        public static StubResponse status(int status, String body) {
            return new StubResponse(status, body, 0);
        }

        public StubResponse delayed(long delayMs) {
            return new StubResponse(status, body, delayMs);
        }
    }

    @FunctionalInterface
    public interface Responder {
        StubResponse respond(Recorded request) throws Exception;
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Responder> routes = new ConcurrentHashMap<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    private StubHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static StubHttpServer start() {
        try {
            return new StubHttpServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public StubHttpServer on(String path, Responder responder) {
        routes.put(path, responder);
        return this;
    }

    public List<Recorded> requests(String path) {
        return requests.stream().filter(r -> r.path().equals(path)).toList();
    }

    public void reset() {
        routes.clear();
        requests.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Recorded recorded = new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                Map.copyOf(exchange.getRequestHeaders()), body);
        requests.add(recorded);
        StubResponse response;
        Responder responder = routes.get(recorded.path());
        try {
            response = responder == null ? StubResponse.status(404, "{\"error\":{\"message\":\"not found\"}}") : responder.respond(recorded);
        } catch (Exception e) {
            response = StubResponse.status(500, "{\"error\":{\"message\":\"stub failure\"}}");
        }
        try {
            if (response.delayMs() > 0) {
                Thread.sleep(response.delayMs());
            }
            byte[] bytes = response.body() == null ? new byte[0] : response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // 用戶端已逾時中斷連線
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
