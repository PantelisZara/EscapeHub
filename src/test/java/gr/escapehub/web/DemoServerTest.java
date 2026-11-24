package gr.escapehub.web;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoServerTest {
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

    private HttpResponse<String> act(HttpClient client, String action) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + "/api/engine-room"))
                .timeout(Duration.ofSeconds(3)).header("Origin", server.url())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("action=" + action)).build(),
                HttpResponse.BodyHandlers.ofString());
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
        HttpResponse<String> initial = get(player, "/api/engine-room");
        assertEquals("{\"steps\":0,\"solved\":false,\"power\":null}", initial.body());
        assertTrue(initial.headers().firstValue("Set-Cookie").orElseThrow().contains("HttpOnly; SameSite=Strict"));

        assertTrue(act(player, "open-valve").body().contains("\"steps\":1"));
        assertEquals(initial.body(), act(player, "start-generator").body());
        act(player, "open-valve");
        act(player, "prime-pump");
        HttpResponse<String> solved = act(player, "start-generator");

        assertEquals(200, solved.statusCode());
        assertEquals("{\"steps\":3,\"solved\":true,\"power\":240}", solved.body());
        assertEquals(solved.body(), get(player, "/api/engine-room").body());
        assertEquals(solved.body(), act(player, "start-generator").body());
    }

    @Test
    void anotherBrowserDoesNotInheritThePlayersProgress() throws Exception {
        get(player, "/api/engine-room");
        act(player, "open-valve");
        try (HttpClient other = newPlayer()) {
            assertTrue(get(other, "/api/engine-room").body().contains("\"steps\":0"));
            assertTrue(get(player, "/api/engine-room").body().contains("\"steps\":1"));
        }
    }

    @Test
    void cannotSubmitWithoutASessionOrInventACompletion() throws Exception {
        assertEquals(403, act(player, "open-valve").statusCode());
        get(player, "/api/engine-room");
        assertEquals(400, act(player, "power%3A240").statusCode());
        assertEquals(400, act(player, "start-generator&solved=true").statusCode());
        assertEquals(413, act(player, "x".repeat(150)).statusCode());
        assertFalse(get(player, "/api/engine-room").body().contains("\"solved\":true"));
    }

    @Test
    void rejectsCrossOriginAndWrongMethodRequests() throws Exception {
        get(player, "/api/engine-room");
        URI uri = URI.create(server.url() + "/api/engine-room");
        HttpRequest attack = HttpRequest.newBuilder(uri).header("Origin", "https://other.example")
                .POST(HttpRequest.BodyPublishers.ofString("action=open-valve")).build();
        assertEquals(403, player.send(attack, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest wrongMethod = HttpRequest.newBuilder(uri).DELETE().build();
        assertEquals(405, player.send(wrongMethod, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertTrue(get(player, "/api/engine-room").body().contains("\"steps\":0"));
    }
}
