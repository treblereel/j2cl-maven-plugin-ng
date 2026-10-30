package org.treblereel.j2cl.plugin;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHttpServerTest {

    @TempDir
    Path directory;
    private Path root;
    private HttpServer server;

    @BeforeEach
    void start() throws Exception {
        root = Files.createDirectory(directory.resolve("web"));
        Files.createDirectory(root.resolve("nested"));
        Files.writeString(root.resolve("nested/test.js"), "valid JavaScript");
        Files.writeString(directory.resolve("secret.txt"), "OUTSIDE_SECRET");
        server = TestHttpServer.create(root);
        server.start();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void servesNormalAssetsAndBindsOnlyToLoopback() throws Exception {
        assertTrue(server.getAddress().getAddress().isLoopbackAddress());
        assertServed("/nested/test.js?cache=1", "valid JavaScript", "application/javascript");
        Files.writeString(root.resolve("nested/test.html"), "valid HTML");
        assertServed("/nested/test.html", "valid HTML", "text/html");
        Files.writeString(root.resolve("nested/test.wasm"), "valid WASM");
        assertServed("/nested/test.wasm", "valid WASM", "application/wasm");
        Files.writeString(root.resolve("nested/a b.js"), "space name");
        assertServed("/nested/a%20b.js", "space name", "application/javascript");
    }

    @Test
    void rejectsTraversalAndEncodedAbsolutePaths() throws Exception {
        Files.createDirectory(directory.resolve("web-other"));
        Files.writeString(directory.resolve("web-other/secret.txt"), "OUTSIDE_SECRET");
        for (String target : new String[]{
                "/../secret.txt", "/%2e%2e/secret.txt", "/%2E%2E%2Fsecret.txt",
                "/nested/../../secret.txt", "/../web-other/secret.txt",
                "/%2f" + directory.resolve("secret.txt").toString().substring(1),
                "/%00bad", "/missing.js", "/nested/"}) {
            assertDenied(target);
        }
        assertServed("/nested/test.js", "valid JavaScript", "application/javascript");
    }

    @Test
    void rejectsFileAndDirectorySymlinksOutsideRoot() throws Exception {
        Files.createSymbolicLink(root.resolve("leak.txt"), directory.resolve("secret.txt"));
        Files.createSymbolicLink(root.resolve("escape"), directory);
        assertDenied("/leak.txt");
        assertDenied("/escape/secret.txt");
    }

    @Test
    void preservesInRootSymlinksAndSymlinkedWebRoot() throws Exception {
        Files.createSymbolicLink(root.resolve("alias.js"), root.resolve("nested/test.js"));
        assertServed("/alias.js", "valid JavaScript", "application/javascript");
        server.stop(0);
        Path linkedRoot = Files.createSymbolicLink(directory.resolve("linked-web"), root);
        server = TestHttpServer.create(linkedRoot);
        server.start();
        assertServed("/nested/test.js", "valid JavaScript", "application/javascript");
        assertDenied("/../secret.txt");
    }

    @Test
    void symlinkMimeTypeUsesRequestedFilename() throws Exception {
        Path blob = Files.writeString(root.resolve("blob.bin"), "linked content");
        Files.createSymbolicLink(root.resolve("alias.wasm"), blob);
        Files.createSymbolicLink(root.resolve("alias.html"), blob);
        Files.createSymbolicLink(root.resolve("alias.js"), blob);
        assertServed("/alias.wasm", "linked content", "application/wasm");
        assertServed("/alias.html", "linked content", "text/html");
        assertServed("/alias.js", "linked content", "application/javascript");
    }

    @Test
    void decodesOnlyOnce() throws Exception {
        Files.createDirectory(root.resolve("%2e%2e"));
        Files.writeString(root.resolve("%2e%2e/test.js"), "literal encoded name");
        assertServed("/%252e%252e/test.js", "literal encoded name", "application/javascript");
    }

    private void assertDenied(String target) throws Exception {
        String response = request(target);
        assertTrue(response.startsWith("HTTP/1.1 404"), target + ": " + response);
        assertFalse(response.contains("OUTSIDE_SECRET"), target);
    }

    private void assertServed(String target, String body, String contentType) throws Exception {
        String response = request(target);
        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.contains(body), response);
        assertTrue(response.toLowerCase(Locale.ROOT).contains("content-type: " + contentType), response);
    }

    private String request(String target) throws Exception {
        // Raw HTTP preserves traversal paths that an HTTP client might normalize away.
        try (Socket socket = new Socket("127.0.0.1", server.getAddress().getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET " + target
                    + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
