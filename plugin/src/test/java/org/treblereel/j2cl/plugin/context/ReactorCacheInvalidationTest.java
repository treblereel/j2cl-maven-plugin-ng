package org.treblereel.j2cl.plugin.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.model.BuildStatus;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.task.OutputTypes;
import org.treblereel.j2cl.plugin.tools.Hashing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactorCacheInvalidationTest {
    @TempDir
    Path directory;

    @Test
    void invalidatesAllConsumersOfSharedLibraryButKeepsIndependentBranch() throws IOException {
        Module library = module("library");
        Module left = module("left", library);
        Module right = module("right", library);
        Module independent = module("independent");
        Module app = module("app", left, right, independent);
        Files.writeString(library.sources.resolve("Source.java"), "changed");

        assertEquals(Set.of("library", "left", "right", "app"),
                ReactorCacheInvalidation.invalidate(app, cache()));
        for (Module module : List.of(library, left, right, app)) {
            assertFalse(Files.exists(marker(module)));
        }
        assertTrue(Files.exists(marker(independent)));
    }

    @Test
    void retainsMarkersWhenInputsHaveNotChanged() throws IOException {
        Module library = module("library");
        Module app = module("app", library);
        String original = Files.readString(marker(app));

        assertTrue(ReactorCacheInvalidation.invalidate(app, cache()).isEmpty());
        assertEquals(original, Files.readString(marker(app)));
        assertTrue(Files.exists(marker(library)));
    }

    @Test
    void missingDependencyMarkerInvalidatesConsumers() throws IOException {
        Module library = module("library");
        Module app = module("app", library);
        Files.delete(marker(library));

        assertEquals(Set.of("library", "app"), ReactorCacheInvalidation.invalidate(app, cache()));
        assertFalse(Files.exists(marker(app)));
    }

    private Path cache() {
        return directory.resolve("cache");
    }

    private Path marker(Module module) {
        return cache().resolve(module.key()).resolve(".success");
    }

    private Module module(String key, Module... dependencies) throws IOException {
        Path sources = Files.createDirectories(directory.resolve(key));
        Files.writeString(sources.resolve("Source.java"), "original");
        Module module = new Module(key, sources, List.of(dependencies));
        BuildStatus status = new BuildStatus();
        status.setHash(Hashing.hash(module.getHashPaths()));
        status.setOutputTypes(Set.of(OutputTypes.TRANSPILED_JS));
        Files.createDirectories(marker(module).getParent());
        Files.writeString(marker(module), new Gson().toJson(status));
        return module;
    }

    private static final class Module extends ReactorDependency {
        private final String key;
        private final Path sources;
        private final List<Dependency> dependencies;

        private Module(String key, Path sources, List<Dependency> dependencies) {
            super(null, null);
            this.key = key;
            this.sources = sources;
            this.dependencies = dependencies;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public List<Path> getHashPaths() {
            return List.of(sources);
        }

        @Override
        public Collection<Dependency> getDependencies() {
            return dependencies;
        }
    }
}
