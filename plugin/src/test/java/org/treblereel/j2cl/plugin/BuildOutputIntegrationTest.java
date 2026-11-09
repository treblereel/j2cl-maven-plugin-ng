package org.treblereel.j2cl.plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "j2cl.integration", matches = "true")
class BuildOutputIntegrationTest {
    @Test
    void loadsGeneratedEsModuleWithItsRealJreImportsAndRelativeWasmUrl() throws Exception {
        Path root = output("wasm-hello-world");
        assertTrue(Files.isRegularFile(root.resolve("wasm-hello-world/wasm-hello-world.module.js")));
        Path page = Files.createTempFile(root, "loader-test-", ".html");
        Files.writeString(page, "<!doctype html><html><title>WASM loader regression</title></html>");
        HttpServer server = TestHttpServer.create(root);
        server.start();
        WebDriver driver = null;
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
            driver = new ChromeDriver(options);
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
            driver.get("http://127.0.0.1:" + server.getAddress().getPort() + "/" + page.getFileName());
            Object result = ((JavascriptExecutor) driver).executeAsyncScript(
                    "const done = arguments[arguments.length - 1];"
                            + "import('./wasm-hello-world/wasm-hello-world.module.js').then(async loader => {"
                            + "const instance = await loader.instantiate();"
                            + "if (instance !== await loader.instantiate()) throw new Error('Instance was not cached');"
                            + "done([instance.exports.add(2, 3), instance.exports.multiply(4, 7)]);"
                            + "}).catch(error => done(String(error.stack || error)));");
            assertEquals(List.of(5L, 28L), result);
        } finally {
            try {
                if (driver != null) driver.quit();
            } finally {
                server.stop(0);
                Files.deleteIfExists(page);
            }
        }
    }

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
