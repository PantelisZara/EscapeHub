package gr.escapehub.web;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoServerTest {
    private static final String ENGINE = "/api/engine-room";
    private static final String ROOM = "/api/room";
    private DemoServer server;
    private HttpClient player;

    @BeforeEach
    void start() throws IOException {
        server = new DemoServer(0);
        server.start();
        player = newPlayer();
    }

    @AfterEach
    void stop() {
        player.close();
        server.close();
    }

    private HttpClient newPlayer() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(3)).build();
    }

    private HttpResponse<String> get(HttpClient client, String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + path)).timeout(Duration.ofSeconds(3))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest formRequest(String path, String form) {
        return HttpRequest.newBuilder(URI.create(server.url() + path))
                .timeout(Duration.ofSeconds(3)).header("Origin", server.url())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
    }

    private HttpResponse<String> post(HttpClient client, String path, String form) throws Exception {
        return client.send(formRequest(path, form), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> act(HttpClient client, String action, int revision) throws Exception {
        return post(client, ENGINE, "action=" + action + "&revision=" + revision);
    }

    private HttpResponse<String> enter(HttpClient client, String name, String code) throws Exception {
        return post(client, ROOM, "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&code=" + code);
    }

    private static String roomCode(HttpResponse<String> response) {
        var match = java.util.regex.Pattern.compile("\"code\":\"([0-9A-F]{8})\"").matcher(response.body());
        assertTrue(match.find());
        return match.group(1);
    }

    private static void assertProgress(HttpResponse<String> response, int steps, boolean solved) {
        assertTrue(response.body().contains("\"steps\":" + steps + ",\"solved\":" + solved
                + ",\"power\":" + (solved ? "240" : "null")), response.body());
    }

    @Test
    void servesTheSceneAndOnlyAllowlistedResources() throws Exception {
        for (String path : List.of("/", "/app.js", "/style.css", "/lighthouse.png")) {
            HttpResponse<String> response = get(player, path);
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Security-Policy").orElseThrow()
                    .contains("frame-ancestors 'none'"));
            assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        }
        assertEquals(404, get(player, "/docs/assignment.pdf").statusCode());
        assertEquals(404, get(player, "/../pom.xml").statusCode());
    }

    @Test
    void illustrationAndFontsAreServedAsUnmodifiedBinaryFiles() throws Exception {
        for (String path : List.of("/lighthouse.png", "/fonts/GFSDidot-Regular.ttf", "/fonts/GFSNeohellenic.ttf")) {
            var response = player.send(HttpRequest.newBuilder(URI.create(server.url() + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertEquals(path.endsWith(".png") ? "image/png" : "font/ttf",
                    response.headers().firstValue("Content-Type").orElseThrow());
            try (var source = DemoServer.class.getResourceAsStream("/web" + path)) {
                assertArrayEquals(source.readAllBytes(), response.body());
            }
        }
    }

    @Test
    void serverChecksTheSequenceAndRetainsProgressOnReload() throws Exception {
        HttpResponse<String> initial = enter(player, "Άννα", "");
        assertEquals(201, initial.statusCode());
        assertProgress(initial, 0, false);
        assertTrue(initial.headers().firstValue("Set-Cookie").orElseThrow().contains("HttpOnly; SameSite=Strict"));

        assertProgress(act(player, "open-valve", 0), 1, false);
        assertProgress(act(player, "start-generator", 1), 0, false);
        act(player, "open-valve", 2);
        act(player, "prime-pump", 3);
        HttpResponse<String> solved = act(player, "start-generator", 4);

        assertEquals(200, solved.statusCode());
        assertProgress(solved, 3, true);
        assertEquals(solved.body(), get(player, ENGINE).body());
        assertEquals(solved.body(), act(player, "start-generator", 5).body());
    }

    @Test
    void joiningSharesThePuzzleAndFindingWithASecondGuest() throws Exception {
        String code = roomCode(enter(player, "Άννα", ""));
        assertProgress(act(player, "open-valve", 0), 1, false);
        try (HttpClient other = newPlayer()) {
            assertEquals(401, get(other, ENGINE).statusCode());
            HttpResponse<String> joined = enter(other, "Μάνος & \"Μ\"", code.toLowerCase(Locale.ROOT));
            assertEquals(200, joined.statusCode());
            assertTrue(joined.body().contains("\"players\":[\"Άννα\",\"Μάνος & \\\"Μ\\\"\"]"));
            assertProgress(joined, 1, false);
            assertEquals(joined.body(), get(player, ENGINE).body());
            assertProgress(act(other, "prime-pump", 1), 2, false);
            HttpResponse<String> solved = act(player, "start-generator", 2);
            assertProgress(solved, 3, true);
            assertEquals(solved.body(), get(other, ENGINE).body());
        }
    }

    @Test
    void separateRoomsHaveSeparateProgressAndUnknownCodesAreRejected() throws Exception {
        assertEquals(404, enter(player, "Άννα", "DEADBEEF").statusCode());
        HttpResponse<String> first = enter(player, "Άννα", "");
        act(player, "open-valve", 0);
        try (HttpClient other = newPlayer()) {
            HttpResponse<String> second = enter(other, "Μάνος", "");
            assertFalse(roomCode(first).equals(roomCode(second)));
            assertProgress(get(other, ENGINE), 0, false);
            assertProgress(get(player, ENGINE), 1, false);
            assertEquals(409, enter(other, "Μάνος", roomCode(first)).statusCode());
            assertEquals(roomCode(second), roomCode(get(other, ENGINE)));
        }
    }

    @Test
    void simultaneousJoinRequestsCanOnlyFillOneRemainingSeat() throws Exception {
        String code = roomCode(enter(player, "Άννα", ""));
        assertEquals(200, enter(player, "Άννα", code).statusCode());
        try (HttpClient other = newPlayer(); HttpClient third = newPlayer()) {
            var firstJoin = other.sendAsync(formRequest(ROOM, "name=Guest+B&code=" + code),
                    HttpResponse.BodyHandlers.ofString());
            var secondJoin = third.sendAsync(formRequest(ROOM, "name=Guest+C&code=" + code),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(List.of(200, 409), List.of(firstJoin.get().statusCode(), secondJoin.get().statusCode())
                    .stream().sorted().toList());
            HttpClient excluded = firstJoin.get().statusCode() == 409 ? other : third;
            assertEquals(401, get(excluded, ENGINE).statusCode());
        }
    }

    @Test
    void simultaneousActionsCannotResetProgressFromTheSameRevision() throws Exception {
        String code = roomCode(enter(player, "Άννα", ""));
        try (HttpClient other = newPlayer()) {
            assertEquals(200, enter(other, "Μάνος", code).statusCode());
            HttpRequest request = formRequest(ENGINE, "action=open-valve&revision=0");
            var first = player.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            var second = other.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(List.of(200, 409), List.of(first.get().statusCode(), second.get().statusCode())
                    .stream().sorted().toList());
            assertEquals(first.get().body(), second.get().body());
            assertProgress(get(other, ENGINE), 1, false);
        }
    }

    @Test
    void cannotSubmitWithoutASessionOrInventACompletion() throws Exception {
        assertEquals(401, act(player, "open-valve", 0).statusCode());
        assertEquals(400, enter(player, "  ", "").statusCode());
        assertEquals(400, enter(player, "Άννα", "xyz").statusCode());
        assertEquals(400, enter(player, "Μ\nΝ", "").statusCode());
        assertEquals(400, post(player, ROOM, "name=Guest&name=Other").statusCode());
        assertEquals(201, enter(player, "Άννα", "").statusCode());
        assertEquals(400, act(player, "power%3A240", 0).statusCode());
        assertEquals(400, act(player, "start-generator&solved=true", 0).statusCode());
        assertEquals(413, act(player, "x".repeat(513), 0).statusCode());
        assertProgress(get(player, ENGINE), 0, false);
    }

    @Test
    void rejectsCrossOriginAndWrongMethodRequests() throws Exception {
        enter(player, "Άννα", "");
        for (String path : List.of(ROOM, ENGINE)) {
            URI uri = URI.create(server.url() + path);
            HttpRequest crossOrigin = HttpRequest.newBuilder(uri).header("Origin", "https://other.example")
                    .POST(HttpRequest.BodyPublishers.ofString("name=Guest")).build();
            assertEquals(403, player.send(crossOrigin, HttpResponse.BodyHandlers.ofString()).statusCode());
            HttpRequest wrongMethod = HttpRequest.newBuilder(uri).DELETE().build();
            assertEquals(405, player.send(wrongMethod, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        assertProgress(get(player, ENGINE), 0, false);
    }
}
