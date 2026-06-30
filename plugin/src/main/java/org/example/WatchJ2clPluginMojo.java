package org.example;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.example.config.BuildConfig;
import org.example.context.ArtifactResolver;
import org.example.context.BuildContext;
import org.example.log.BuildLog;
import org.example.log.MavenBuildLog;
import org.example.model.Dependency;
import org.example.model.ReactorDependency;
import org.example.task.FinalTask;
import org.example.task.TaskInput;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.*;

@Mojo(
        name = "watch",
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class WatchJ2clPluginMojo extends AbstractJ2clPluginMojo {

    @Parameter(defaultValue = "SIMPLE_OPTIMIZATIONS", property = "j2cl.watch.compilationLevel")
    protected String watchCompilationLevel;

    @Parameter(defaultValue = "true", property = "j2cl.watch.enableSourcemaps")
    protected boolean watchEnableSourcemaps;

    @Override
    protected void process(ReactorDependency project, BuildContext buildContext, BuildLog buildLog) {
        throw new UnsupportedOperationException("WatchJ2clPluginMojo uses its own execute() flow");
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
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

        List<File> extraClasspath = Arrays.asList(
                getFileWithMavenCoords(jreJar),
                getFileWithMavenCoords(jsinteropAnnotationsJar),
                getFileWithMavenCoords(internalAnnotationsJar),
                getFileWithMavenCoords(jsinteropBaseJar),
                getFileWithMavenCoords(jspecify)
        );

        List<Artifact> extraJsZips = Arrays.asList(
                getMavenArtifactWithCoords(bootstrapJsZip),
                getMavenArtifactWithCoords(jreJsZip)
        );

        File bootstrapClasspathFile = getFileWithMavenCoords(this.bootstrapClasspath);

        BuildConfig buildConfig = new BuildConfig(
                extraClasspath, extraJsZips, bootstrapClasspathFile,
                initialScriptFilename, webappDirectory, watchCompilationLevel,
                defines, rewritePolyfills, translationsFile, watchEnableSourcemaps,
                languageOut, true, env,
                annotationProcessorsArgs
        );

        BuildContext buildContext = new BuildContext(
                project, buildConfig, artifactResolver,
                new PluginParameterExpressionEvaluator(session, mojoExecution)
        );

        ReactorDependency reactorProject = new ReactorDependency(project, artifactResolver);

        doWatch(reactorProject, buildContext, buildLog);
    }

    private void doWatch(ReactorDependency project, BuildContext buildContext, BuildLog buildLog) {
        buildLog.info("Running initial compilation...");
        try {
            new FinalTask(project, buildContext, buildLog).runTask().join();
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

                buildLog.info("Detected changes in " + changedFiles.size() + " file(s):");
                changedFiles.stream().limit(10).forEach(f -> buildLog.info("  " + f));
                if (changedFiles.size() > 10) {
                    buildLog.info("  ... and " + (changedFiles.size() - 10) + " more");
                }

                rebuild(project, buildContext, buildLog);
            }
        } catch (InterruptedException e) {
            buildLog.info("Watch interrupted, stopping.");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            buildLog.error("Watch service error: " + e.getMessage());
            throw new RuntimeException("Watch service failed", e);
        }
    }

    private void rebuild(ReactorDependency project, BuildContext buildContext, BuildLog buildLog) {
        buildLog.info("Recompiling...");
        long start = System.currentTimeMillis();

        clearCacheRecursive(project, buildContext, new HashSet<>());
        buildContext.resetFailed();

        try {
            new FinalTask(project, buildContext, buildLog).runTask().join();
            long elapsed = System.currentTimeMillis() - start;
            buildLog.info(String.format("Recompilation complete in %.1fs", elapsed / 1000.0));
        } catch (Exception e) {
            buildLog.error("Recompilation failed: " + e.getMessage());
            buildLog.info("Waiting for next change...");
        }
    }

    private void deleteSuccessMarkers(Path projectOutputDir) {
        if (!Files.exists(projectOutputDir)) return;
        try (Stream<Path> walk = Files.walk(projectOutputDir)) {
            walk.filter(p -> p.getFileName().toString().equals(".success"))
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    private void clearCacheRecursive(ReactorDependency dep, BuildContext buildContext, Set<String> visited) {
        if (!visited.add(dep.key())) return;
        TaskInput.clearCacheForDependency(dep.key());
        deleteSuccessMarkers(buildContext.getOutputDirectory().resolve(dep.key()));
        for (Dependency child : dep.getDependencies()) {
            if (child instanceof ReactorDependency reactorChild) {
                clearCacheRecursive(reactorChild, buildContext, visited);
            }
        }
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
