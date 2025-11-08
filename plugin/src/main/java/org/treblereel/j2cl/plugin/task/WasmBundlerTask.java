package org.treblereel.j2cl.plugin.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.tools.WasmBundler;

public class WasmBundlerTask extends TaskInput {

    public WasmBundlerTask(Dependency dep, BuildContext buildContext, BuildLog buildLog) {
        super(dep, buildContext, buildLog);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.WASM_BUNDLED;
    }

    @Override
    public void process() {
        List<Dependency> allDeps = deduplicateAndSort(getFlattenDependencies());

        TaskOutput selfWasm = input(dependency, OutputTypes.TRANSPILED_WASM);
        TaskOutput depsWasm = input(allDeps, OutputTypes.TRANSPILED_WASM);

        List<Path> moduleDirs = new ArrayList<>();

        File jreJsZip = buildContext.getConfig().getWasmJreJsZip();
        if (jreJsZip != null && jreJsZip.exists()) {
            Path jreWasmDir = extractJreWasmFragments(jreJsZip);
            if (jreWasmDir != null) {
                moduleDirs.add(jreWasmDir);
            }
        }

        moduleDirs.addAll(depsWasm.paths());
        moduleDirs.addAll(selfWasm.paths());

        Path watOutput = outputPath().resolve("module.wat");
        Path jsImportsOutput = outputPath().resolve("imports.js.txt");

        Map<String, String> defines = new HashMap<>(Map.of(
                "J2WASM_DEBUG", "TRUE",
                "jre.strictFpToString", "DISABLED",
                "jre.checkedMode", "ENABLED",
                "jre.checks.checkLevel", "MINIMAL",
                "jre.checks.bounds", "AUTO",
                "jre.checks.api", "AUTO",
                "jre.checks.numeric", "AUTO",
                "jre.checks.type", "AUTO",
                "jre.logging.logLevel", "ALL",
                "jre.logging.simpleConsoleHandler", "ENABLED"
        ));
        defines.put("jre.classMetadata", "SIMPLE");
        for (Map.Entry<String, Object> e : buildContext.getConfig().defines().entrySet()) {
            defines.put(e.getKey(), String.valueOf(e.getValue()));
        }

        List<String> wasmEntryPoints = buildContext.getConfig().wasmEntryPoints();
        WasmBundler bundler = new WasmBundler(logger, defines, wasmEntryPoints);
        if (!bundler.bundle(moduleDirs, watOutput, jsImportsOutput)) {
            throw new RuntimeException("WASM bundling failed for " + dependency.key());
        }
    }

    private List<Dependency> deduplicateAndSort(List<Dependency> deps) {
        Map<String, Dependency> seen = new LinkedHashMap<>();
        for (Dependency dep : deps) {
            String ga = dep.groupId() + ":" + dep.artifactId();
            seen.putIfAbsent(ga, dep);
        }
        List<Dependency> unique = new ArrayList<>(seen.values());
        return topologicalSort(unique);
    }

    private List<Dependency> topologicalSort(List<Dependency> deps) {
        Set<String> scope = new HashSet<>();
        for (Dependency dep : deps) {
            scope.add(dep.groupId() + ":" + dep.artifactId());
        }

        Map<String, Dependency> byGa = new LinkedHashMap<>();
        for (Dependency dep : deps) {
            byGa.put(dep.groupId() + ":" + dep.artifactId(), dep);
        }

        Set<String> visited = new LinkedHashSet<>();
        List<Dependency> sorted = new ArrayList<>();

        for (Dependency dep : deps) {
            visit(dep, visited, sorted, scope, byGa);
        }
        return sorted;
    }

    private void visit(Dependency dep, Set<String> visited, List<Dependency> sorted,
                        Set<String> scope, Map<String, Dependency> byGa) {
        String ga = dep.groupId() + ":" + dep.artifactId();
        if (!visited.add(ga)) {
            return;
        }
        for (Dependency child : dep.getDependencies()) {
            String childGa = child.groupId() + ":" + child.artifactId();
            if (scope.contains(childGa) && byGa.containsKey(childGa)) {
                visit(byGa.get(childGa), visited, sorted, scope, byGa);
            }
        }
        sorted.add(dep);
    }

    private Path extractJreWasmFragments(File zipFile) {
        Path extractDir = outputPath().resolve("jre-wasm-fragments");
        try {
            Files.createDirectories(extractDir);
            try (ZipFile zf = new ZipFile(zipFile)) {
                String fragmentsDir = findFragmentsDir(zf);
                if (fragmentsDir == null) {
                    logger.error("No WASM fragments found in JRE jszip: " + zipFile);
                    return null;
                }

                extractFile(zf, fragmentsDir + "types.wat", extractDir.resolve("types.wat"));
                extractFile(zf, fragmentsDir + "contents.wat", extractDir.resolve("contents.wat"));
                extractFile(zf, fragmentsDir + "summary.binpb", extractDir.resolve("summary.binpb"));
                extractFile(zf, fragmentsDir + "imports.wat", extractDir.resolve("imports.wat"));
            }
            return extractDir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract JRE WASM fragments from " + zipFile, e);
        }
    }

    private String findFragmentsDir(ZipFile zf) {
        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().endsWith("/types.wat")) {
                return entry.getName().substring(0, entry.getName().length() - "types.wat".length());
            }
        }
        return null;
    }

    private void extractFile(ZipFile zf, String entryName, Path target) throws IOException {
        ZipEntry entry = zf.getEntry(entryName);
        if (entry != null) {
            Files.copy(zf.getInputStream(entry), target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
