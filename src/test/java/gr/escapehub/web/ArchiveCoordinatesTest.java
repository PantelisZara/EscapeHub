package gr.escapehub.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchiveCoordinatesTest {
    @Test
    void liveLookupPassesTheWikidataCoordinatesToNominatim() throws IOException {
        var query = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/wikidata", exchange -> reply(exchange,
                "<api><value latitude=\"36.35780\" longitude=\"25.35707\""
                        + " globe=\"http://www.wikidata.org/entity/Q2\"/></api>"));
        server.createContext("/reverse", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            reply(exchange, "<reversegeocode><result osm_type=\"node\" osm_id=\"1446175116\""
                    + " lat=\"36.3577023\" lon=\"25.3571183\"/></reversegeocode>");
        });
        server.start();
        try {
            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            var coordinates = ArchiveCoordinates.live(URI.create(origin + "/wikidata"), URI.create(origin + "/reverse"));
            assertEquals("format=xml&lat=36.35780&lon=25.35707&zoom=18&addressdetails=0&accept-language=en", query.get());
            assertEquals(new ArchiveCoordinates("36.35780, 25.35707", "36.3577023, 25.3571183"), coordinates);
        } finally {
            server.stop(0);
        }
    }

    private static void reply(HttpExchange exchange, String xml) throws IOException {
        try (exchange) {
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
