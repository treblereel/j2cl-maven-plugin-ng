package org.treblereel.j2cl.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.ArtifactResolver;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.log.MavenBuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.task.BundleJarTask;
import org.treblereel.j2cl.plugin.task.FinalTask;
import org.treblereel.j2cl.plugin.task.TaskInput;
import org.treblereel.j2cl.plugin.task.WasmFinalTask;
import org.treblereel.j2cl.plugin.tools.JavacInternals;
import org.treblereel.j2cl.plugin.xbt.XtbResolver;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

@Mojo(
        name = "watch",
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class WatchJ2clPluginMojo extends AbstractJ2clPluginMojo {

    @Parameter(property = "j2cl.watch.compilationLevel")
    protected String watchCompilationLevel;

    @Parameter(defaultValue = "true", property = "j2cl.watch.enableSourcemaps")
    protected boolean watchEnableSourcemaps;

    @Override
    protected void process(ReactorDependency project, BuildContext buildContext, BuildLog buildLog) {
        throw new UnsupportedOperationException("WatchJ2clPluginMojo uses its own execute() flow");
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        JavacInternals.openToPlugin();
        BuildLog buildLog = new MavenBuildLog(this);

        Map<String, org.apache.maven.artifact.Artifact> defaultDependencyReplacement = new HashMap<>();
        defaultDependencyReplacement.put("com.google.jsinterop:base", new org.apache.maven.artifact.DefaultArtifact(
                "org.kie.j2cl.tools.jsinterop", "jsinterop-base", "1.1.1",
                "compile", "jar", null, new org.apache.maven.artifact.handler.DefaultArtifactHandler()
        ));
        defaultDependencyReplacement.put("org.gwtproject:gwt-user", null);
        defaultDependencyReplacement.put("org.gwtproject:gwt-dev", null);
        defaultDependencyReplacement.put("org.gwtproject:gwt-servlet", null);
        defaultDependencyReplacement.put("com.google.gwt:gwt-user", null);
        defaultDependencyReplacement.put("com.google.gwt:gwt-dev", null);
        defaultDependencyReplacement.put("com.google.gwt:gwt-servlet", null);

        ArtifactResolver artifactResolver = new ArtifactResolver(
                project, repoSystem, remoteRepos, repoSession, session,
                projectBuilder, defaultDependencyReplacement, buildLog
        );

        boolean isWasm = "WASM".equalsIgnoreCase(backend);

        List<File> extraClasspath = Arrays.asList(
                getFileWithMavenCoords(isWasm ? jreWasmJar : jreJar),
                getFileWithMavenCoords(jsinteropAnnotationsJar),
                getFileWithMavenCoords(internalAnnotationsJar),
                getFileWithMavenCoords(jsinteropBaseJar),
                getFileWithMavenCoords(jspecify)
        );

        List<Artifact> extraJsZips;
        if (isWasm) {
            extraJsZips = List.of();
        } else {
            extraJsZips = Arrays.asList(
                    getMavenArtifactWithCoords(bootstrapJsZip),
                    getMavenArtifactWithCoords(jreJsZip)
            );
        }

        File bootstrapClasspathFile = getFileWithMavenCoords(
                isWasm ? this.bootstrapClasspathWasm : this.bootstrapClasspath);

        File wasmJreJsZip = isWasm
                ? getFileWithMavenCoords(this.jreWasmJsZip) : null;

        BuildConfig buildConfig = new BuildConfig(
                extraClasspath, extraJsZips, bootstrapClasspathFile,
                initialScriptFilename, webappDirectory,
                resolveWatchCompilationLevel(),
                defines, rewritePolyfills, translationsFile, watchEnableSourcemaps,
                languageOut, true, env,
                annotationProcessorsArgs,
                List.of(),
                backend, wasmEntryPoints, wasmJreJsZip
        );

        try (BuildContext buildContext = new BuildContext(
                project, buildConfig, artifactResolver,
                new PluginParameterExpressionEvaluator(session, mojoExecution)
        )) {
            buildContext.setWatchMode(true);

            ReactorDependency reactorProject = new ReactorDependency(project, artifactResolver);

            List<File> xtbFiles =
                    XtbResolver.resolveTranslationsFiles(
                            translationsFile, defines, this.project.getBasedir(),
                            buildContext.getAdditionalXtbSearchPaths(), buildLog);
            for (File xtbFile : xtbFiles) {
                reactorProject.addAdditionalSourcePath(xtbFile.toPath());
            }

            doWatch(reactorProject, buildContext, buildLog);
        }
    }

    private String resolveWatchCompilationLevel() {
        if (watchCompilationLevel != null) {
            return watchCompilationLevel;
        }
        return compilationLevel;
    }

    private void runPipeline(ReactorDependency project, BuildContext buildContext, BuildLog buildLog) {
        if ("WASM".equalsIgnoreCase(buildContext.getConfig().backend())) {
            new WasmFinalTask(project, buildContext, buildLog).runTask().join();
        } else if ("BUNDLE_JAR".equalsIgnoreCase(buildContext.getConfig().compilationLevel())) {
            new BundleJarTask(project, buildContext, buildLog).runTask().join();
        } else {
            new FinalTask(project, buildContext, buildLog).runTask().join();
        }
    }

    private void doWatch(ReactorDependency project, BuildContext buildContext, BuildLog buildLog) {
        buildLog.info("Running initial compilation...");
        try {
            runPipeline(project, buildContext, buildLog);
            buildLog.info("Initial compilation complete.");
        } catch (Exception e) {
            buildLog.error("Initial compilation failed: " + e.getMessage());
        }

        List<Path> sourcePaths = new ArrayList<>(project.getSourcePaths());
        collectReactorSourcePaths(project, sourcePaths, new HashSet<>());
        if (sourcePaths.isEmpty()) {
            buildLog.warn("No source directories to watch");
            return;
        }

        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            Map<WatchKey, Path> keyToDir = new HashMap<>();
            for (Path sourceRoot : sourcePaths) {
                registerRecursive(watcher, sourceRoot, keyToDir);
                buildLog.info("Watching: " + sourceRoot);
            }

            buildLog.info("Watching for changes... (press Ctrl+C to stop)");

            while (true) {
                WatchKey key = watcher.take();
                Path dir = keyToDir.get(key);
                if (dir == null) {
                    key.cancel();
                    continue;
                }

                Set<String> changedFiles = new LinkedHashSet<>();
                drainEvents(key, dir, changedFiles, watcher, keyToDir);

                Thread.sleep(200);
                WatchKey extraKey;
                while ((extraKey = watcher.poll()) != null) {
                    Path extraDir = keyToDir.get(extraKey);
                    if (extraDir != null) {
                        drainEvents(extraKey, extraDir, changedFiles, watcher, keyToDir);
                    } else {
                        extraKey.cancel();
                    }
                }

                if (changedFiles.isEmpty()) {
                    continue;
                }

                logChangedFiles(buildLog, changedFiles);

                Set<String> pendingChanges = rebuildAndMonitor(
                        project, buildContext, buildLog, changedFiles, watcher, keyToDir);

                while (!pendingChanges.isEmpty()) {
                    logChangedFiles(buildLog, pendingChanges);
                    pendingChanges = rebuildAndMonitor(
                            project, buildContext, buildLog, pendingChanges, watcher, keyToDir);
                }
            }
        } catch (InterruptedException e) {
            buildLog.info("Watch interrupted, stopping.");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            buildLog.error("Watch service error: " + e.getMessage());
            throw new RuntimeException("Watch service failed", e);
        }
    }

    private void logChangedFiles(BuildLog buildLog, Set<String> changedFiles) {
        buildLog.info("Detected changes in " + changedFiles.size() + " file(s):");
        changedFiles.stream().limit(10).forEach(f -> buildLog.info("  " + f));
        if (changedFiles.size() > 10) {
            buildLog.info("  ... and " + (changedFiles.size() - 10) + " more");
        }
    }

    private Set<String> rebuildAndMonitor(ReactorDependency project, BuildContext buildContext,
                                          BuildLog buildLog, Set<String> changedFiles,
                                          WatchService watcher, Map<WatchKey, Path> keyToDir)
            throws InterruptedException {
        Thread buildThread = Thread.ofVirtual().name("j2cl-rebuild").start(() ->
                rebuild(project, buildContext, buildLog, changedFiles));

        Set<String> pendingChanges = new LinkedHashSet<>();
        while (buildThread.isAlive()) {
            WatchKey pendingKey = watcher.poll(200, TimeUnit.MILLISECONDS);
            if (pendingKey != null) {
                Path pendingDir = keyToDir.get(pendingKey);
                if (pendingDir != null) {
                    drainEvents(pendingKey, pendingDir, pendingChanges, watcher, keyToDir);
                } else {
                    pendingKey.cancel();
                }
            }
        }
        buildThread.join();

        // Drain any final stale events
        Thread.sleep(200);
        WatchKey staleKey;
        while ((staleKey = watcher.poll()) != null) {
            Path staleDir = keyToDir.get(staleKey);
            if (staleDir != null) {
                drainEvents(staleKey, staleDir, pendingChanges, watcher, keyToDir);
            } else {
                staleKey.cancel();
            }
        }

        // Filter out files that were already in the current build
        pendingChanges.removeAll(changedFiles);
        return pendingChanges;
    }

    private void rebuild(ReactorDependency project, BuildContext buildContext,
                         BuildLog buildLog, Set<String> changedFiles) {
        buildLog.info("Recompiling...");
        long start = System.currentTimeMillis();

        Set<String> changedModules = findChangedModules(project, changedFiles, new HashSet<>());
        Set<String> dirtyModules = findDirtyModules(project, changedModules, new HashSet<>());

        clearDirtyModules(project, buildContext, dirtyModules, new HashSet<>());
        buildContext.resetFailed();

        try {
            runPipeline(project, buildContext, buildLog);
            long elapsed = System.currentTimeMillis() - start;
            buildLog.info(String.format("Recompilation complete in %.1fs", elapsed / 1000.0));
        } catch (Exception e) {
            buildLog.error("Recompilation failed: " + e.getMessage());
            buildLog.info("Waiting for next change...");
        }
    }

    private Set<String> findChangedModules(ReactorDependency dep, Set<String> changedFiles, Set<String> visited) {
        Set<String> result = new HashSet<>();
        collectChangedModules(dep, changedFiles, result, visited);
        return result;
    }

    private void collectChangedModules(ReactorDependency dep, Set<String> changedFiles,
                                       Set<String> result, Set<String> visited) {
        if (!visited.add(dep.key())) return;
        for (Path sourcePath : dep.getSourcePaths()) {
            String sourceDir = sourcePath.toString();
            if (changedFiles.stream().anyMatch(f -> f.startsWith(sourceDir))) {
                result.add(dep.key());
                break;
            }
        }
        for (Dependency child : dep.getDependencies()) {
            if (child instanceof ReactorDependency reactorChild) {
                collectChangedModules(reactorChild, changedFiles, result, visited);
            }
        }
    }

    private Set<String> findDirtyModules(ReactorDependency dep, Set<String> changedModules, Set<String> visited) {
        Set<String> dirty = new HashSet<>();
        computeDirty(dep, changedModules, dirty, visited);
        return dirty;
    }

    private boolean computeDirty(ReactorDependency dep, Set<String> changedModules,
                                 Set<String> dirty, Set<String> visited) {
        if (!visited.add(dep.key())) return dirty.contains(dep.key());

        boolean isDirty = changedModules.contains(dep.key());

        for (Dependency child : dep.getDependencies()) {
            if (child instanceof ReactorDependency reactorChild) {
                if (computeDirty(reactorChild, changedModules, dirty, visited)) {
                    isDirty = true;
                }
            }
        }

        if (isDirty) {
            dirty.add(dep.key());
        }
        return isDirty;
    }

    private void clearDirtyModules(ReactorDependency dep, BuildContext buildContext,
                                   Set<String> dirtyModules, Set<String> visited) {
        if (!visited.add(dep.key())) return;

        if (dirtyModules.contains(dep.key())) {
            TaskInput.clearCacheForDependency(dep.key());
            deleteSuccessMarkers(buildContext.getOutputDirectory().resolve(dep.key()));
        }

        for (Dependency child : dep.getDependencies()) {
            if (child instanceof ReactorDependency reactorChild) {
                clearDirtyModules(reactorChild, buildContext, dirtyModules, visited);
            }
        }
    }

    private void deleteSuccessMarkers(Path projectOutputDir) {
        if (!Files.exists(projectOutputDir)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(projectOutputDir)) {
            walk.filter(p -> p.getFileName().toString().equals(".success"))
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    private void collectReactorSourcePaths(ReactorDependency dep, List<Path> paths, Set<String> visited) {
        for (Dependency child : dep.getDependencies()) {
            if (child instanceof ReactorDependency reactorChild && visited.add(reactorChild.key())) {
                paths.addAll(reactorChild.getSourcePaths());
                collectReactorSourcePaths(reactorChild, paths, visited);
            }
        }
    }

    private void registerRecursive(WatchService watcher, Path root, Map<WatchKey, Path> keyToDir) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                keyToDir.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void drainEvents(WatchKey key, Path dir, Set<String> changedFiles,
                             WatchService watcher, Map<WatchKey, Path> keyToDir) {
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == OVERFLOW) continue;

            @SuppressWarnings("unchecked")
            Path name = ((WatchEvent<Path>) event).context();
            Path child = dir.resolve(name);
            changedFiles.add(child.toString());

            if (kind == ENTRY_CREATE && Files.isDirectory(child)) {
                try {
                    registerRecursive(watcher, child, keyToDir);
                } catch (IOException ignored) {}
            }
        }
        if (!key.reset()) {
            keyToDir.remove(key);
        }
    }
}
