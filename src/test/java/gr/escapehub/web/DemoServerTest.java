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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
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
    private static final String LANTERN = "/api/lantern";
    private static final String ACCOUNTS = "/api/accounts";
    private static final Pattern COMPLETION =
            Pattern.compile("\"completion\":\\{\"completedAt\":\"([^\"]+)\",\"durationSeconds\":(\\d+)\\}");
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
        var match = Pattern.compile("\"code\":\"([0-9A-F]{8})\"").matcher(response.body());
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
    void radioShowsPrivateSignalsAndRequiresBothSessions() throws Exception {
        assertEquals(401, get(player, "/api/radio").statusCode());
        assertEquals(401, post(player, "/api/radio", "signal=000000").statusCode());
        String code = roomCode(enter(player, "Άννα", ""));
        String firstSignal = radioSignal(player);
        assertEquals(409, post(player, "/api/radio", "signal=" + firstSignal).statusCode());

        try (HttpClient partner = newPlayer(); HttpClient outsider = newPlayer()) {
            assertEquals(200, enter(partner, "Άννα", code).statusCode());
            String secondSignal = radioSignal(partner);
            assertFalse(firstSignal.equals(secondSignal));
            assertFalse(get(player, "/api/radio").body().contains(secondSignal));
            assertFalse(get(partner, "/api/radio").body().contains(firstSignal));
            assertFalse(get(player, ENGINE).body().contains("\"signal\""));
            assertEquals(422, post(player, "/api/radio", "signal=" + firstSignal).statusCode());
            assertEquals(400, post(player, "/api/radio", "signal=" + secondSignal + "&seat=1").statusCode());

            assertEquals(200, post(player, "/api/radio", "signal=" + secondSignal).statusCode());
            assertEquals(200, post(player, "/api/radio", "signal=" + secondSignal).statusCode());
            assertTrue(get(partner, "/api/radio").body().contains("\"partnerConfirmed\":true"));
            assertTrue(get(player, ENGINE).body().contains("\"message\":null"));

            assertEquals(200, post(partner, "/api/radio",
                    "signal=" + firstSignal.toLowerCase(Locale.ROOT)).statusCode());
            assertTrue(get(player, ENGINE).body().contains("\"message\":\"Τρεις σύντομες αναλαμπές\""));
            assertEquals(get(player, ENGINE).body(), get(partner, ENGINE).body());
            assertTrue(get(partner, "/api/radio").body().contains("\"confirmed\":true"));
            assertProgress(get(player, ENGINE), 0, false);
            assertEquals(201, enter(outsider, "Μάνος", "").statusCode());
            assertTrue(get(outsider, ENGINE).body().contains("\"message\":null"));
        }
    }

    @Test
    void archivePublishesTheVerifiedLocationOnlyInTheSolvedRoom() throws Exception {
        String archive = "/api/archive";
        assertEquals(401, get(player, archive).statusCode());
        assertEquals(401, post(player, archive, "marker=B").statusCode());
        String code = roomCode(enter(player, "Alex", ""));
        try (HttpClient partner = newPlayer(); HttpClient otherRoom = newPlayer()) {
            assertEquals(200, enter(partner, "Dana", code).statusCode());
            assertEquals(201, enter(otherRoom, "Sam", "").statusCode());
            var clue = get(player, archive);
            assertEquals(200, clue.statusCode());
            assertTrue(clue.body().contains("\"coordinates\":\"36.35779, 25.35706\""));
            assertTrue(clue.body().contains("\"B\":\"36.3577023, 25.3571183\""));
            assertEquals(400, post(player, archive, "marker=D").statusCode());
            assertEquals(422, post(player, archive, "marker=A").statusCode());
            assertTrue(get(partner, ENGINE).body().contains("\"location\":null"));

            assertEquals(200, post(player, archive, "marker=B").statusCode());
            assertEquals(200, post(partner, archive, "marker=B").statusCode());
            assertTrue(get(partner, ENGINE).body().contains("\"location\":\"36.3577023, 25.3571183\""));
            assertTrue(get(otherRoom, ENGINE).body().contains("\"location\":null"));
            assertProgress(get(player, ENGINE), 0, false);
        }
    }

    @Test
    void lanternRequiresAllThreeFindingsAndSharesCompletionInEitherOrder() throws Exception {
        String answer = "location=36.3577023%2C25.3571183&power=240&message="
                + URLEncoder.encode("Τρεις σύντομες αναλαμπές", StandardCharsets.UTF_8);
        assertEquals(401, post(player, LANTERN, answer).statusCode());
        var orders = List.of(List.of("archive", "engine", "radio"), List.of("radio", "archive", "engine"));
        for (var order : orders) {
            try (HttpClient first = newPlayer(); HttpClient partner = newPlayer()) {
                Instant beforeRoom = Instant.now();
                String code = roomCode(enter(first, "Alex", ""));
                assertEquals(200, enter(partner, "Dana", code).statusCode());
                for (String path : order) {
                    var early = post(first, LANTERN, answer);
                    assertEquals(409, early.statusCode());
                    assertTrue(early.body().contains("\"lanternReady\":false,\"lanternLit\":false"));
                    solvePath(first, partner, path);
                }
                var ready = get(partner, ENGINE).body();
                assertTrue(ready.contains("\"power\":240,\"message\":\"Τρεις σύντομες αναλαμπές\","
                        + "\"location\":\"36.3577023, 25.3571183\""));
                assertTrue(ready.contains("\"lanternReady\":true,\"lanternLit\":false"));
                assertTrue(ready.contains("\"completion\":null"));
                assertEquals(400, post(first, LANTERN, "power=240").statusCode());
                var wrong = post(first, LANTERN, answer.replace("power=240", "power=241V"));
                assertEquals(422, wrong.statusCode());
                assertTrue(wrong.body().contains("\"lanternLit\":false"));
                assertTrue(wrong.body().contains("\"completion\":null"));
                String formattedPower = order.getFirst().equals("archive") ? "240V" : "240+V";
                var solved = post(first, LANTERN, answer.replace("power=240", "power=" + formattedPower));
                assertEquals(200, solved.statusCode());
                assertTrue(solved.body().contains("\"lanternLit\":true"));
                var completion = COMPLETION.matcher(solved.body());
                assertTrue(completion.find(), solved.body());
                Instant completedAt = Instant.parse(completion.group(1));
                assertFalse(completedAt.isBefore(beforeRoom));
                assertFalse(completedAt.isAfter(Instant.now()));
                var retry = post(partner, LANTERN, answer);
                assertEquals(200, retry.statusCode());
                assertEquals(solved.body(), retry.body());
                assertEquals(solved.body(), get(partner, ENGINE).body());
            }
        }
    }

    private void solvePath(HttpClient first, HttpClient partner, String path) throws Exception {
        switch (path) {
            case "archive" -> assertEquals(200, post(first, "/api/archive", "marker=B").statusCode());
            case "engine" -> {
                assertProgress(act(first, "open-valve", 0), 1, false);
                assertProgress(act(first, "prime-pump", 1), 2, false);
                assertProgress(act(first, "start-generator", 2), 3, true);
            }
            case "radio" -> {
                assertEquals(200, post(first, "/api/radio", "signal=" + radioSignal(partner)).statusCode());
                assertEquals(200, post(partner, "/api/radio", "signal=" + radioSignal(first)).statusCode());
            }
            default -> throw new AssertionError("Unknown path " + path);
        }
    }

    private String radioSignal(HttpClient client) throws Exception {
        HttpResponse<String> response = get(client, "/api/radio");
        assertEquals(200, response.statusCode());
        var match = java.util.regex.Pattern.compile("\"signal\":\"([0-9A-F]{6})\"").matcher(response.body());
        assertTrue(match.find());
        return match.group(1);
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
    void registersUniqueAccountsAndRejectsInvalidOrDuplicateRequests() throws Exception {
        String encodedUser = URLEncoder.encode("Θάνος", StandardCharsets.UTF_8);
        HttpResponse<String> created = post(player, ACCOUNTS, "username=" + encodedUser + "&password=Password123");
        assertEquals(201, created.statusCode());
        assertEquals("{\"username\":\"Θάνος\"}", created.body());
        assertFalse(created.body().contains("Password123"));

        HttpResponse<String> duplicate = post(player, ACCOUNTS,
                "username=" + URLEncoder.encode("θάνος", StandardCharsets.UTF_8) + "&password=OtherPass123");
        assertEquals(409, duplicate.statusCode());

        assertEquals(400, post(player, ACCOUNTS, "username=ab&password=Password123").statusCode());
        assertEquals(400, post(player, ACCOUNTS, "username=validuser&password=short").statusCode());
        assertEquals(400, post(player, ACCOUNTS, "username=validuser").statusCode());
        assertEquals(400, post(player, ACCOUNTS, "username=validuser&password=Password123&extra=1").statusCode());
    }

    @Test
    void rejectsCrossOriginAndWrongMethodRequests() throws Exception {
        enter(player, "Άννα", "");
        for (String path : List.of(ROOM, ENGINE, ACCOUNTS)) {
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
