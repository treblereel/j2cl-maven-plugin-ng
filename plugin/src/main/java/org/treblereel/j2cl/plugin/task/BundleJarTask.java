package org.treblereel.j2cl.plugin.task;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerInput;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.deps.ClosureBundler;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.SortedDependencies;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.transpile.BaseTranspiler;
import com.google.javascript.jscomp.transpile.Transpiler;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;

public class BundleJarTask extends TaskInput {

    private static final PathMatcher BUNDLE_JS = withSuffix(ClosureBundleTask.BUNDLE_JS_EXTENSION);

    public BundleJarTask(Dependency dep, BuildContext buildContext, BuildLog buildLog) {
        super(dep, buildContext, buildLog);
    }

    @Override
    public OutputTypes getOutputTypes() {
        return OutputTypes.BUNDLED_JS_APP;
    }

    @Override
    public void process() {
        TaskOutput selfBundled = input(dependency, OutputTypes.BUNDLED_JS);
        List<Dependency> allDeps = getFlattenDependencies();
        TaskOutput depsBundled = input(allDeps, OutputTypes.BUNDLED_JS);

        TaskOutput selfTranspiled = input(dependency, OutputTypes.TRANSPILED_JS);
        TaskOutput depsTranspiled = input(allDeps, OutputTypes.TRANSPILED_JS);

        Path webappDirectory = Paths.get(buildContext.getConfig().webappDirectory());
        String scriptFilename = buildContext.getConfig().initialScriptFilename();
        Path scriptFile = webappDirectory.resolve(scriptFilename);
        Path scriptDir = scriptFile.getParent();

        try {
            Files.createDirectories(scriptDir);

            List<Path> orderedBundleFiles = collectBundleFilesInOrder(selfBundled, depsBundled, allDeps);

            for (Path bundleFile : orderedBundleFiles) {
                Path targetFile = scriptDir.resolve(bundleFile.getFileName());
                Files.copy(bundleFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }

            List<Path> jsZipBundleFiles = bundleJsZipFiles(scriptDir);

            Path sourcesDir = scriptDir.resolve("sources");
            Files.createDirectories(sourcesDir);

            Stream.concat(selfTranspiled.files().stream(), depsTranspiled.files().stream())
                    .forEach(fe -> {
                        try {
                            Path targetPath = sourcesDir.resolve(fe.getSourcePath());
                            Files.createDirectories(targetPath.getParent());
                            Files.copy(fe.getAbsolutePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to copy source file " + fe.getSourcePath(), e);
                        }
                    });

            for (File zipFile : buildContext.getConfig().getJsZip()) {
                extractZipSources(zipFile, sourcesDir);
            }

            List<String> allBundleNames = new ArrayList<>();
            for (Path jsZipBundle : jsZipBundleFiles) {
                allBundleNames.add(jsZipBundle.getFileName().toString());
            }
            for (Path bundleFile : orderedBundleFiles) {
                allBundleNames.add(bundleFile.getFileName().toString());
            }

            generateLoaderScript(scriptFile, allBundleNames);

            copyPublicResources(scriptDir);

        } catch (IOException e) {
            throw new RuntimeException("Failed to assemble BUNDLE_JAR output for " + dependency.key(), e);
        }
    }

    private List<Path> collectBundleFilesInOrder(TaskOutput selfBundled, TaskOutput depsBundled, List<Dependency> allDeps) {
        List<FileEntry> selfBundles = selfBundled.filter(BUNDLE_JS).files();
        List<FileEntry> depBundles = depsBundled.filter(BUNDLE_JS).files();

        Map<String, List<FileEntry>> bundlesByDep = new LinkedHashMap<>();
        for (FileEntry fe : depBundles) {
            String depKey = extractDepKeyFromPath(fe);
            bundlesByDep.computeIfAbsent(depKey, k -> new ArrayList<>()).add(fe);
        }

        List<Path> ordered = new ArrayList<>();
        Set<String> added = new HashSet<>();

        List<Dependency> sorted = topologicalSort(allDeps);
        for (Dependency dep : sorted) {
            String key = dep.key();
            if (added.contains(key)) continue;
            added.add(key);
            List<FileEntry> bundles = bundlesByDep.get(key);
            if (bundles != null) {
                for (FileEntry fe : bundles) {
                    try {
                        if (Files.size(fe.getAbsolutePath()) > 0) {
                            ordered.add(fe.getAbsolutePath());
                        }
                    } catch (IOException e) {
                        ordered.add(fe.getAbsolutePath());
                    }
                }
            }
        }

        for (FileEntry fe : selfBundles) {
            try {
                if (Files.size(fe.getAbsolutePath()) > 0) {
                    ordered.add(fe.getAbsolutePath());
                }
            } catch (IOException e) {
                ordered.add(fe.getAbsolutePath());
            }
        }

        return ordered;
    }

    private String extractDepKeyFromPath(FileEntry fe) {
        Path absPath = fe.getAbsolutePath();
        for (int i = 0; i < absPath.getNameCount(); i++) {
            if (absPath.getName(i).toString().equals("j2cl-plugin-cache") && i + 1 < absPath.getNameCount()) {
                return absPath.getName(i + 1).toString();
            }
        }
        return absPath.getParent().getParent().getFileName().toString();
    }

    private List<Dependency> topologicalSort(List<Dependency> deps) {
        List<Dependency> result = new ArrayList<>();
        Set<String> resolved = new HashSet<>();
        List<Dependency> remaining = new ArrayList<>(deps);
        remaining.sort(Comparator.comparingInt(d -> d.getDependencies().size()));

        int lastSize = -1;
        while (!remaining.isEmpty() && remaining.size() != lastSize) {
            lastSize = remaining.size();
            for (Iterator<Dependency> it = remaining.iterator(); it.hasNext(); ) {
                Dependency dep = it.next();
                boolean allDepsResolved = dep.getDependencies().stream()
                        .allMatch(d -> resolved.contains(d.key()) || !remaining.contains(d));
                if (allDepsResolved) {
                    it.remove();
                    resolved.add(dep.key());
                    result.add(dep);
                }
            }
        }
        result.addAll(remaining);
        return result;
    }

    private List<Path> bundleJsZipFiles(Path outputDir) throws IOException {
        List<File> jsZips = buildContext.getConfig().getJsZip();
        List<Path> bundleFiles = new ArrayList<>();

        for (File zipFile : jsZips) {
            List<SourceFile> sourceFiles;
            try {
                sourceFiles = SourceFile.fromZipFile(zipFile.toPath().toString(), Charset.defaultCharset());
            } catch (IOException e) {
                throw new RuntimeException("Unable to read jsZip " + zipFile, e);
            }

            if (sourceFiles.isEmpty()) continue;

            List<ClosureBundleTask.DependencyInfoAndSource> dependencyInfos = new ArrayList<>();
            Compiler jsCompiler = new Compiler(System.err);

            for (SourceFile sf : sourceFiles) {
                CompilerInput input = new CompilerInput(sf);
                input.setCompiler(jsCompiler);
                dependencyInfos.add(new ClosureBundleTask.DependencyInfoAndSource(input, input::getCode));
            }

            Set<String> sourceRoots = new HashSet<>();
            for (SourceFile sf : sourceFiles) {
                String name = sf.getName();
                int lastSlash = name.lastIndexOf('/');
                if (lastSlash > 0) {
                    sourceRoots.add(name.substring(0, lastSlash));
                }
            }

            SortedDependencies<ClosureBundleTask.DependencyInfoAndSource> sorter;
            try {
                sorter = new SortedDependencies<>(dependencyInfos);
            } catch (Exception e) {
                throw new RuntimeException("Failed to sort dependencies in jsZip " + zipFile, e);
            }

            ClosureBundler bundler = new ClosureBundler(Transpiler.NULL, new BaseTranspiler(
                    new BaseTranspiler.CompilerSupplier(
                            CompilerOptions.LanguageMode.ECMASCRIPT_NEXT.toFeatureSet().without(FeatureSet.Feature.MODULES),
                            ModuleLoader.ResolutionMode.BROWSER,
                            ImmutableList.copyOf(sourceRoots),
                            ImmutableMap.of()
                    ),
                    ""
            )).useEval(true);

            String safeName = zipFile.getName().replaceAll("[^\\-_a-zA-Z0-9.]", "-")
                    .replaceAll("\\.zip$|\\.jar$", "");
            Path bundleFile = outputDir.resolve(safeName + ClosureBundleTask.BUNDLE_JS_EXTENSION);

            try (BufferedWriter bundleOut = new BufferedWriter(
                    new OutputStreamWriter(Files.newOutputStream(bundleFile), StandardCharsets.UTF_8))) {

                for (ClosureBundleTask.DependencyInfoAndSource info : sorter.getSortedList()) {
                    String code = info.getSource();
                    String name = info.getName();

                    if (Compiler.isFillFileName(name) && code.isEmpty()) continue;

                    bundleOut.append("//").append(name).append("\n");
                    bundler.withPath(name).withSourceUrl("sources/" + name).appendTo(bundleOut, info, code);
                    bundleOut.append("\n");
                }
            }

            bundleFiles.add(bundleFile);
        }

        return bundleFiles;
    }

    private void generateLoaderScript(Path scriptFile, List<String> bundleNames) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Map<String, Object> defines = new LinkedHashMap<>(buildContext.getConfig().defines());
        defines.put("goog.ENABLE_DEBUG_LOADER", false);

        String defineLine = "var CLOSURE_UNCOMPILED_DEFINES = " + gson.toJson(defines) + ";\n";

        String intro = "(function() {" +
                "var src = document.currentScript.src;\n" +
                "var lastSlash = src.lastIndexOf('/');\n" +
                "var base = lastSlash === -1 ? '' : src.substr(0, lastSlash + 1);";

        String scriptsArray = gson.toJson(bundleNames);

        String outro = ".forEach(file => {\n" +
                "  var elt = document.createElement('script');\n" +
                "  elt.src = base + file;\n" +
                "  elt.type = 'text/javascript';\n" +
                "  elt.async = false;\n" +
                "  document.head.appendChild(elt);\n" +
                "});" + "})();";

        StringBuilder runtime = new StringBuilder();
        new ClosureBundler().appendRuntimeTo(runtime);

        Files.write(scriptFile, Arrays.asList(
                defineLine,
                intro,
                scriptsArray,
                outro,
                runtime
        ));
    }

    private void copyPublicResources(Path outputDir) {
        TaskOutput selfUnzipped = input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES);
        TaskOutput depsUnzipped = input(getFlattenDependencies(), OutputTypes.UNZIPPED_DEPENDENCIES);

        PathMatcher IN_META_INF_RESOURCES = path -> path.startsWith(Paths.get("META-INF", "resources"));
        PathMatcher IN_PUBLIC = path -> {
            for (Path part : path) {
                if (part.toString().equals("public")) return true;
            }
            return false;
        };

        List<FileEntry> resources = Stream.concat(
                selfUnzipped.filter(IN_META_INF_RESOURCES, IN_PUBLIC).files().stream(),
                depsUnzipped.filter(IN_META_INF_RESOURCES, IN_PUBLIC).files().stream()
        ).toList();

        for (FileEntry resource : resources) {
            try {
                Path outPath = outputDir.resolve(extractPublicResourcePath(resource.getSourcePath()));
                Files.createDirectories(outPath.getParent());
                Files.copy(resource.getAbsolutePath(), outPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Unable to copy resource: " + resource.getSourcePath(), e);
            }
        }
    }

    private Path extractPublicResourcePath(Path p) {
        int publicIndex = -1;
        for (int i = 0; i < p.getNameCount(); i++) {
            if (p.getName(i).toString().equals("public")) {
                publicIndex = i;
                break;
            }
        }
        if (publicIndex >= 0 && publicIndex + 1 < p.getNameCount()) {
            return p.subpath(publicIndex + 1, p.getNameCount());
        }
        if (p.startsWith(Paths.get("META-INF", "resources"))) {
            return p.subpath(2, p.getNameCount());
        }
        return p;
    }

    private void extractZipSources(File zipFile, Path sourceMapDir) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile)) {
            String commonPrefix = findCommonPrefix(zf);
            if (commonPrefix == null) return;
            String prefix = commonPrefix.isEmpty() ? "" : commonPrefix + "/";

            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.startsWith(prefix)) continue;
                String relative = name.substring(prefix.length());
                Path targetPath = sourceMapDir.resolve(relative);
                Files.createDirectories(targetPath.getParent());
                try (InputStream is = zf.getInputStream(entry)) {
                    Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String findCommonPrefix(ZipFile zf) {
        String commonDir = null;
        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String name = entry.getName();
            String dir = name.contains("/") ? name.substring(0, name.lastIndexOf('/')) : "";
            if (commonDir == null) {
                commonDir = dir;
            } else {
                while (!commonDir.isEmpty() && !dir.startsWith(commonDir)) {
                    int lastSlash = commonDir.lastIndexOf('/');
                    commonDir = lastSlash >= 0 ? commonDir.substring(0, lastSlash) : "";
                }
            }
        }
        return commonDir;
    }
}
