package org.treblereel.j2cl.plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "j2cl.integration", matches = "true")
class BuildOutputIntegrationTest {
    @Test
    void publishesStaticResourcesAtTheirPublicUrls() throws Exception {
        HttpServer server = TestHttpServer.create(output("static-resources"));
        server.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/static-resources/";
            for (String resource : List.of("publicfile.js", "metainfresourcesfile.js")) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + resource)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), resource);
            }
            var hidden = client.send(HttpRequest.newBuilder(URI.create(base + "ignoredfile.js")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, hidden.statusCode());
        } finally {
            server.stop(0);
        }
    }

    private Path output(String artifactId) {
        return Path.of(System.getProperty("basedir")).getParent().resolve("tests/" + artifactId + "/target")
                .resolve(artifactId + "-" + Versions.PLUGIN_VERSION);
    }

}
