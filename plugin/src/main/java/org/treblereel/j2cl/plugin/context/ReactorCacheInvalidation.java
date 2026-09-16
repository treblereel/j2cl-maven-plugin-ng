package org.treblereel.j2cl.plugin.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.treblereel.j2cl.plugin.model.BuildStatus;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.task.TaskInput;
import org.treblereel.j2cl.plugin.tools.Hashing;

/** Invalidates changed reactor modules and their consumers before a cached root task can return. */
public final class ReactorCacheInvalidation {
    private ReactorCacheInvalidation() {
    }

    public static Set<String> invalidate(ReactorDependency root, Path outputDirectory) {
        Map<String, ReactorDependency> modules = new LinkedHashMap<>();
        Map<String, Set<String>> consumers = new HashMap<>();
        collect(root, modules, consumers);

        Set<String> dirty = new LinkedHashSet<>();
        for (ReactorDependency module : modules.values()) {
            if (hasChanged(module, outputDirectory)) {
                markConsumers(module.key(), consumers, dirty);
            }
        }
        for (String key : dirty) {
            TaskInput.clearCacheForDependency(key);
            try {
                Files.deleteIfExists(outputDirectory.resolve(key).resolve(".success"));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to invalidate reactor module " + key, e);
            }
        }
        return Set.copyOf(dirty);
    }

    private static void collect(ReactorDependency module, Map<String, ReactorDependency> modules,
                                Map<String, Set<String>> consumers) {
        if (modules.putIfAbsent(module.key(), module) != null) {
            return;
        }
        for (Dependency dependency : module.getDependencies()) {
            if (dependency instanceof ReactorDependency child) {
                consumers.computeIfAbsent(child.key(), key -> new LinkedHashSet<>()).add(module.key());
                collect(child, modules, consumers);
            }
        }
    }

    private static boolean hasChanged(ReactorDependency module, Path outputDirectory) {
        Path marker = outputDirectory.resolve(module.key()).resolve(".success");
        if (!Files.isRegularFile(marker)) {
            return true;
        }
        try {
            BuildStatus status = new Gson().fromJson(Files.readString(marker), BuildStatus.class);
            return status == null || status.getHash() == null || status.getOutputTypes() == null
                    || !status.getHash().equals(Hashing.hash(module.getHashPaths()));
        } catch (JsonParseException e) {
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read reactor cache marker " + marker, e);
        }
    }

    private static void markConsumers(String key, Map<String, Set<String>> consumers, Set<String> dirty) {
        if (dirty.add(key)) {
            for (String consumer : consumers.getOrDefault(key, Set.of())) {
                markConsumers(consumer, consumers, dirty);
            }
        }
    }
}
