package gr.escapehub.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import gr.escapehub.mission.MissionProgress;
import gr.escapehub.mission.RadioPuzzle;
import gr.escapehub.mission.SequentialPuzzle;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class DemoServer implements AutoCloseable {
    private static final int MAX_ROOMS = 64;
    private static final int MAX_PLAYERS = 2;
    private static final int MAX_FORM_BYTES = 512;
    private static final List<String> MECHANISMS = List.of("open-valve", "prime-pump", "start-generator");
    private static final Map<String, String> ASSETS = Map.of(
            "/", "index.html", "/style.css", "style.css", "/app.js", "app.js",
            "/lighthouse.png", "lighthouse.png",
            "/fonts/GFSDidot-Regular.ttf", "fonts/GFSDidot-Regular.ttf",
            "/fonts/GFSNeohellenic.ttf", "fonts/GFSNeohellenic.ttf");
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();

    public DemoServer(int port) throws IOException {
        this("127.0.0.1", port);
    }

    private DemoServer(String bindAddress, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
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
        String bindAddress = System.getenv().getOrDefault("ESCAPEHUB_BIND_ADDRESS", "127.0.0.1");
        DemoServer demo = new DemoServer(bindAddress, args.length == 0 ? 8080 : Integer.parseInt(args[0]));
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
            if (path.equals("/api/room")) {
                enterRoom(exchange, method);
            } else if (path.equals("/api/engine-room")) {
                engineRoom(exchange, method);
            } else if (path.equals("/api/radio")) {
                radio(exchange, method);
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

    private void enterRoom(HttpExchange exchange, String method) throws IOException {
        if (!method.equals("POST")) {
            methodNotAllowed(exchange, "POST");
            return;
        }
        Map<String, String> form = readForm(exchange);
        if (form == null) {
            return;
        }
        String name = form.getOrDefault("name", "").strip();
        String code = form.getOrDefault("code", "").strip().toUpperCase(Locale.ROOT);
        if (!Set.of("name", "code").containsAll(form.keySet()) || name.isBlank() || name.length() > 24
                || name.codePoints().anyMatch(Character::isISOControl) || !code.matches("(?:[0-9A-F]{8})?")) {
            respond(exchange, 400, "text/plain", "Invalid guest name or room code");
            return;
        }

        Entry entry = register(cookieId(exchange), name, code);
        if (entry.room() == null) {
            respond(exchange, entry.status(), "text/plain", entry.message());
            return;
        }
        if (entry.sessionId() != null) {
            exchange.getResponseHeaders().set("Set-Cookie",
                    "escapehub=" + entry.sessionId() + "; Path=/; HttpOnly; SameSite=Strict");
        }
        respond(exchange, entry.status(), "application/json", entry.room().snapshot());
    }

    private synchronized Entry register(String sessionId, String name, String code) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            Room existing = session.room();
            if (!code.isEmpty() && !existing.code.equals(code)) {
                return new Entry(409, null, null, "Already in a room");
            }
            return new Entry(200, existing, null, null);
        }

        Room room;
        if (code.isEmpty()) {
            if (rooms.size() >= MAX_ROOMS) {
                return new Entry(503, null, null, "Demo full; restart the server");
            }
            String newCode = HexFormat.of().withUpperCase().toHexDigits(random.nextInt());
            while (rooms.containsKey(newCode)) {
                newCode = HexFormat.of().withUpperCase().toHexDigits(random.nextInt());
            }
            String firstSignal = newSignal();
            String secondSignal = newSignal();
            while (secondSignal.equals(firstSignal)) {
                secondSignal = newSignal();
            }
            room = new Room(newCode, firstSignal, secondSignal);
            rooms.put(newCode, room);
        } else {
            room = rooms.get(code);
            if (room == null) {
                return new Entry(404, null, null, "Room not found");
            }
        }
        int seat = room.addPlayer(name);
        if (seat < 0) {
            return new Entry(409, null, null, "Room full");
        }
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(room, seat));
        return new Entry(code.isEmpty() ? 201 : 200, room, id, null);
    }

    private String newSignal() {
        return HexFormat.of().withUpperCase().toHexDigits(random.nextInt(), 6);
    }

    private synchronized Session session(HttpExchange exchange) {
        return sessions.get(cookieId(exchange));
    }

    private void engineRoom(HttpExchange exchange, String method) throws IOException {
        if (!method.equals("GET") && !method.equals("POST")) {
            methodNotAllowed(exchange, "GET, POST");
            return;
        }
        Session guest = session(exchange);
        if (guest == null) {
            respond(exchange, 401, "text/plain", "Join a room before playing");
            return;
        }
        Room room = guest.room();
        if (method.equals("GET")) {
            respond(exchange, 200, "application/json", room.snapshot());
            return;
        }
        Map<String, String> form = readForm(exchange);
        if (form == null) {
            return;
        }
        String action = form.getOrDefault("action", "");
        String revision = form.getOrDefault("revision", "");
        if (form.size() != 2 || !MECHANISMS.contains(action) || !revision.matches("[0-9]{1,18}")) {
            respond(exchange, 400, "text/plain", "Invalid mechanism or revision");
            return;
        }
        ActionResult result = room.act(action, Long.parseLong(revision));
        respond(exchange, result.status(), "application/json", result.body());
    }

    private void radio(HttpExchange exchange, String method) throws IOException {
        if (!method.equals("GET") && !method.equals("POST")) {
            methodNotAllowed(exchange, "GET, POST");
            return;
        }
        Session guest = session(exchange);
        if (guest == null) {
            respond(exchange, 401, "text/plain", "Join a room before playing");
            return;
        }
        if (method.equals("GET")) {
            respond(exchange, 200, "application/json", guest.room().radioSnapshot(guest.seat()));
            return;
        }
        Map<String, String> form = readForm(exchange);
        if (form == null) {
            return;
        }
        String signal = form.getOrDefault("signal", "").strip().toUpperCase(Locale.ROOT);
        if (form.size() != 1 || !signal.matches("[0-9A-F]{6}")) {
            respond(exchange, 400, "text/plain", "Invalid radio signal");
            return;
        }
        ActionResult result = guest.room().confirmSignal(guest.seat(), signal);
        respond(exchange, result.status(), "application/json", result.body());
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String host = exchange.getRequestHeaders().getFirst("Host");
        String origin = "http://" + host;
        String local = "http://localhost:" + server.getAddress().getPort();
        if ((!origin.equals(url()) && !origin.equals(local))
                || !origin.equals(exchange.getRequestHeaders().getFirst("Origin"))) {
            respond(exchange, 403, "text/plain", "Same-origin request required");
            return null;
        }
        if (!"application/x-www-form-urlencoded".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) {
            respond(exchange, 415, "text/plain", "Form data required");
            return null;
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_FORM_BYTES + 1);
        if (body.length > MAX_FORM_BYTES) {
            respond(exchange, 413, "text/plain", "Form data too large");
            return null;
        }
        Map<String, String> form = new HashMap<>();
        try {
            for (String field : new String(body, StandardCharsets.UTF_8).split("&", -1)) {
                String[] pair = field.split("=", 2);
                if (pair.length != 2 || form.putIfAbsent(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pair[1], StandardCharsets.UTF_8)) != null) {
                    respond(exchange, 400, "text/plain", "Invalid form data");
                    return null;
                }
            }
        } catch (IllegalArgumentException malformedEncoding) {
            respond(exchange, 400, "text/plain", "Invalid form encoding");
            return null;
        }
        return form;
    }

    private static String cookieId(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        return cookie == null ? "" : Arrays.stream(cookie.split(";"))
                .map(String::trim).filter(value -> value.startsWith("escapehub="))
                .map(value -> value.substring("escapehub=".length())).findFirst().orElse("");
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

    private record Entry(int status, Room room, String sessionId, String message) { }

    private record Session(Room room, int seat) { }

    private record ActionResult(int status, String body) { }

    private static final class Room {
        private final String code;
        private final List<String> players = new ArrayList<>(MAX_PLAYERS);
        private final MissionProgress progress = new MissionProgress(List.of("archive", "engine-room", "radio"));
        private final SequentialPuzzle puzzle = new SequentialPuzzle(progress, "engine-room", MECHANISMS, "power:240");
        private final RadioPuzzle radio;
        private long revision;

        Room(String code, String firstSignal, String secondSignal) {
            this.code = code;
            radio = new RadioPuzzle(progress, firstSignal, secondSignal);
        }

        synchronized int addPlayer(String name) {
            if (players.size() == MAX_PLAYERS) {
                return -1;
            }
            players.add(name);
            return players.size() - 1;
        }

        synchronized ActionResult confirmSignal(int seat, String signal) {
            if (players.size() < MAX_PLAYERS) {
                return new ActionResult(409, radioSnapshot(seat));
            }
            boolean accepted = radio.confirm(seat, signal);
            return new ActionResult(accepted ? 200 : 422, radioSnapshot(seat));
        }

        synchronized String radioSnapshot(int seat) {
            RadioPuzzle.View view = radio.view(seat);
            return String.format(Locale.ROOT,
                    "{\"signal\":\"%s\",\"ready\":%s,\"confirmed\":%s,\"partnerConfirmed\":%s,\"message\":%s}",
                    view.signal(), players.size() == MAX_PLAYERS, view.confirmed(), view.partnerConfirmed(),
                    quote(view.message()));
        }

        synchronized ActionResult act(String action, long expectedRevision) {
            // A delayed click must not reset progress made by the other player.
            if (revision != expectedRevision) {
                return new ActionResult(409, snapshot());
            }
            int previousSteps = puzzle.completedSteps();
            puzzle.act(action);
            if (previousSteps != puzzle.completedSteps()) {
                revision++;
            }
            return new ActionResult(200, snapshot());
        }

        synchronized String snapshot() {
            boolean solved = progress.solvedPaths().contains("engine-room");
            String names = players.stream().map(Room::quote).collect(Collectors.joining(","));
            return String.format(Locale.ROOT,
                    "{\"code\":\"%s\",\"players\":[%s],\"revision\":%d,\"steps\":%d,\"solved\":%s,"
                            + "\"power\":%s,\"message\":%s}",
                    code, names, revision, puzzle.completedSteps(), solved, solved ? "240" : "null",
                    quote(radio.message()));
        }

        private static String quote(String text) {
            return text == null ? "null" : "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
