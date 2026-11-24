package gr.escapehub.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import gr.escapehub.mission.MissionProgress;
import gr.escapehub.mission.SequentialPuzzle;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DemoServer implements AutoCloseable {
    private static final Map<String, String> ASSETS = Map.of(
            "/", "index.html", "/style.css", "style.css", "/app.js", "app.js",
            "/lighthouse.png", "lighthouse.png",
            "/fonts/GFSDidot-Regular.ttf", "fonts/GFSDidot-Regular.ttf",
            "/fonts/GFSNeohellenic.ttf", "fonts/GFSNeohellenic.ttf");
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public DemoServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public static void main(String[] args) throws IOException {
        DemoServer demo = new DemoServer(args.length == 0 ? 8080 : Integer.parseInt(args[0]));
        Runtime.getRuntime().addShutdownHook(new Thread(demo::close));
        demo.start();
        System.out.println("EscapeHub: " + demo.url());
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/api/engine-room")) {
                engineRoom(exchange, method);
            } else if (ASSETS.containsKey(path)) {
                if (!method.equals("GET")) {
                    methodNotAllowed(exchange, "GET");
                    return;
                }
                String type = switch (path) {
                    case "/style.css" -> "text/css";
                    case "/app.js" -> "text/javascript";
                    case "/lighthouse.png" -> "image/png";
                    case "/fonts/GFSDidot-Regular.ttf", "/fonts/GFSNeohellenic.ttf" -> "font/ttf";
                    default -> "text/html";
                };
                try (var resource = DemoServer.class.getResourceAsStream("/web/" + ASSETS.get(path))) {
                    if (resource == null) {
                        respond(exchange, 404, "text/plain", "Not found");
                    } else {
                        respond(exchange, 200, type, resource.readAllBytes());
                    }
                }
            } else {
                respond(exchange, 404, "text/plain", "Not found");
            }
        }
    }

    private void engineRoom(HttpExchange exchange, String method) throws IOException {
        if (!method.equals("GET") && !method.equals("POST")) {
            methodNotAllowed(exchange, "GET, POST");
            return;
        }
        if (method.equals("POST") && !url().equals(exchange.getRequestHeaders().getFirst("Origin"))) {
            respond(exchange, 403, "text/plain", "Same-origin request required");
            return;
        }
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        String id = cookie == null ? "" : Arrays.stream(cookie.split(";"))
                .map(String::trim).filter(value -> value.startsWith("escapehub="))
                .map(value -> value.substring("escapehub=".length())).findFirst().orElse("");
        Session session = sessions.get(id);
        if (session == null && method.equals("GET")) {
            synchronized (sessions) {
                if (sessions.size() >= 128) {
                    respond(exchange, 503, "text/plain", "Demo full; restart the server");
                    return;
                }
                id = UUID.randomUUID().toString();
                session = new Session();
                sessions.put(id, session);
            }
            exchange.getResponseHeaders().set("Set-Cookie",
                    "escapehub=" + id + "; Path=/; HttpOnly; SameSite=Strict");
        }
        if (session == null) {
            respond(exchange, 403, "text/plain", "Open the demo before playing");
            return;
        }
        String action = null;
        if (method.equals("POST")) {
            if (!"application/x-www-form-urlencoded".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                respond(exchange, 415, "text/plain", "Form action required");
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(129);
            if (body.length > 128) {
                respond(exchange, 413, "text/plain", "Action too large");
                return;
            }
            action = switch (new String(body, StandardCharsets.UTF_8)) {
                case "action=open-valve" -> "open-valve";
                case "action=prime-pump" -> "prime-pump";
                case "action=start-generator" -> "start-generator";
                default -> null;
            };
            if (action == null) {
                respond(exchange, 400, "text/plain", "Unknown mechanism");
                return;
            }
        }
        synchronized (session) {
            if (action != null) {
                session.puzzle.act(action);
            }
            boolean solved = session.progress.solvedPaths().contains("engine-room");
            respond(exchange, 200, "application/json", "{\"steps\":%d,\"solved\":%s,\"power\":%s}".formatted(
                    session.puzzle.completedSteps(), solved, solved ? "240" : "null"));
        }
    }

    private static void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        respond(exchange, 405, "text/plain", "Method not allowed");
    }

    private static void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        respond(exchange, status, type + "; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    private static final class Session {
        private final MissionProgress progress = new MissionProgress(List.of("archive", "engine-room", "radio"));
        private final SequentialPuzzle puzzle = new SequentialPuzzle(progress, "engine-room",
                List.of("open-valve", "prime-pump", "start-generator"), "power:240");
    }
}
