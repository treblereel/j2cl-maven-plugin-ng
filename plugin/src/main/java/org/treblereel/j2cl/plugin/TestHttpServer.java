package org.treblereel.j2cl.plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;

final class TestHttpServer {

    private TestHttpServer() {
    }

    static HttpServer create(Path webRoot) throws IOException {
        Path root = webRoot.toRealPath();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                // getPath decodes percent escapes once, before validating the filesystem path.
                ResolvedFile file = resolveFile(root, exchange.getRequestURI().getPath());
                if (file == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] bytes = Files.readAllBytes(file.real());
                String name = file.requested().getFileName().toString();
                String contentType;
                if (name.endsWith(".html")) contentType = "text/html";
                else if (name.endsWith(".js")) contentType = "application/javascript";
                else if (name.endsWith(".wasm")) contentType = "application/wasm";
                else {
                    contentType = Files.probeContentType(file.requested());
                    if (contentType == null) contentType = "application/octet-stream";
                }
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        return server;
    }

    private record ResolvedFile(Path requested, Path real) {
    }

    private static ResolvedFile resolveFile(Path root, String requestPath) {
        if (requestPath == null || !requestPath.startsWith("/")) return null;
        try {
            Path relative = Path.of(requestPath.substring(1));
            if (relative.isAbsolute()) return null;
            Path candidate = root.resolve(relative).normalize();
            if (!candidate.startsWith(root)) return null;
            // Lexical containment alone does not prevent symlinks escaping the web root.
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(root) || !Files.isRegularFile(realFile)) return null;
            return new ResolvedFile(candidate, realFile);
        } catch (IOException | InvalidPathException e) {
            return null;
        }
    }
}
