package gr.escapehub.web;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

record ArchiveCoordinates(String archive, String map) {
    private static final URI WIKIDATA = URI.create(
            "https://www.wikidata.org/w/api.php?action=wbgetclaims&entity=Q60750512&property=P625&format=xml");
    private static final URI NOMINATIM = URI.create("https://nominatim.openstreetmap.org/reverse");
    private static final int MAX_RESPONSE_BYTES = 32_768;

    static ArchiveCoordinates snapshot() throws IOException {
        Properties snapshot = new Properties();
        try (var resource = ArchiveCoordinates.class.getResourceAsStream("/data/akrotiri-lighthouse.properties")) {
            if (resource == null) {
                throw new IOException("Missing lighthouse snapshot");
            }
            snapshot.load(resource);
        }
        String archive = snapshot.getProperty("archive.coordinates");
        String map = snapshot.getProperty("map.coordinates");
        if (archive == null || map == null) {
            throw new IOException("Incomplete lighthouse snapshot");
        }
        return new ArchiveCoordinates(archive, map);
    }

    static ArchiveCoordinates live() throws IOException {
        return live(WIKIDATA, NOMINATIM);
    }

    static ArchiveCoordinates live(URI wikidata, URI nominatim) throws IOException {
        Element coordinate = readElement(wikidata, "value");
        if (!"http://www.wikidata.org/entity/Q2".equals(coordinate.getAttribute("globe"))) {
            throw new IOException("Wikidata coordinate is not on Earth");
        }
        String latitude = degrees(coordinate, "latitude", 90);
        String longitude = degrees(coordinate, "longitude", 180);
        URI reverse = URI.create(nominatim + "?format=xml&lat=" + latitude + "&lon=" + longitude
                + "&zoom=18&addressdetails=0&accept-language=en");
        Element feature = readElement(reverse, "result");
        if (!"node".equals(feature.getAttribute("osm_type")) || !"1446175116".equals(feature.getAttribute("osm_id"))) {
            throw new IOException("OpenStreetMap did not return the lighthouse node");
        }
        return new ArchiveCoordinates(latitude + ", " + longitude,
                degrees(feature, "lat", 90) + ", " + degrees(feature, "lon", 180));
    }

    private static String degrees(Element element, String attribute, int maximum) throws IOException {
        String value = element.getAttribute(attribute);
        if (!value.matches("-?[0-9]{1,3}(?:\\.[0-9]{1,15})?") || Math.abs(Double.parseDouble(value)) > maximum) {
            throw new IOException("Invalid archive coordinate: " + attribute);
        }
        return value;
    }

    private static Element readElement(URI endpoint, String name) throws IOException {
        var connection = (HttpURLConnection) endpoint.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setRequestProperty("User-Agent", "EscapeHub/0.1 (https://github.com/PantelisZara/EscapeHub)");
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException(endpoint.getHost() + " returned HTTP " + connection.getResponseCode());
            }
            try (var response = connection.getInputStream()) {
                byte[] body = response.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) {
                    throw new IOException("Oversized archive response from " + endpoint.getHost());
                }
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                var matches = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body)).getElementsByTagName(name);
                if (matches.getLength() != 1) {
                    throw new IOException("Expected one " + name + " from " + endpoint.getHost());
                }
                return (Element) matches.item(0);
            }
        } catch (ParserConfigurationException | SAXException malformed) {
            throw new IOException("Could not read archive response from " + endpoint.getHost(), malformed);
        } finally {
            connection.disconnect();
        }
    }
}
