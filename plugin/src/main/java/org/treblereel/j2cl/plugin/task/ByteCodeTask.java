package org.treblereel.j2cl.plugin.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.JarDependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.model.TestReactorDependency;
import org.treblereel.j2cl.plugin.tools.AptPath;
import org.treblereel.j2cl.plugin.tools.Javac;
import org.treblereel.j2cl.plugin.tools.MavenResourceInputs;

import static org.treblereel.j2cl.plugin.tools.J2CLModuleParser.getSuperSourcePaths;
import static org.treblereel.j2cl.plugin.tools.ServiceFileReader.readProcessors;

public class ByteCodeTask extends TaskInput {

    public static final String APT_RESOURCES = "apt-resources";

    public static final PathMatcher JAVA_SOURCES = withSuffix(".java");
    public static final PathMatcher TURBINE_OUTPUT = filename("output.jar");

    public ByteCodeTask(Dependency dep, BuildContext buildContext, BuildLog logger) {
        super(dep, buildContext, logger);
    }

    public OutputTypes getOutputTypes() {
        return OutputTypes.BYTECODE;
    }

    @Override
    public void process() {
        List<File> extraClasspath = buildContext.getConfig().getExtraClasspath();

        List<Path> superSourcePaths = getSuperSourcePaths(
                input(dependency, OutputTypes.UNZIPPED_DEPENDENCIES).paths());

        TaskOutput self = input(dependency, OutputTypes.STRIPPED_SOURCES).filter(JAVA_SOURCES);

        List<String> sources = self.files().stream()
                .filter(f -> superSourcePaths.stream().noneMatch(f.getSourcePath()::startsWith))
                .map(f -> f.getAbsolutePath().toString())
                .toList();

        List<Dependency> dependencies = getFlattenDependencies();
        Set<String> moduleDependencies = Stream.concat(extraClasspath.stream().map(File::toString),
                        input(dependencies, OutputTypes.BYTECODE)
                                .filter(TURBINE_OUTPUT)
                                .files()
                                .stream()
                                .map(f -> f.getAbsolutePath().toFile().toString()))
                .collect(Collectors.toSet());

        File output = new File(outputPath().toFile(), "output.jar");

        List<String> annotationProcessorJars = new ArrayList<>();
        List<String> annotationProcessorNames = new ArrayList<>();

        if (dependency.isSourceMapped() && !buildContext.isIgnoreMavenAnnotationProcessors()) {
            MavenProject project = ((ReactorDependency)dependency).getMavenProject();
            List<AptPath> aptPaths = buildContext.getAPTProcessorPaths(project, dependency instanceof TestReactorDependency);
            for (AptPath aptPath : aptPaths) {
                moduleDependencies.add(aptPath.annotationProcessorFile().getAbsolutePath());
                annotationProcessorJars.add(aptPath.annotationProcessorFile().getAbsolutePath());
                annotationProcessorNames.addAll(aptPath.annotationProcessorName());
            }

            for (Artifact artifact : project.getDependencyArtifacts()) {
                if (!"provided".equals(artifact.getScope()) || artifact.getFile() == null) continue;
                File file = artifact.getFile();
                List<String> processors = List.of();
                try {
                    if (file.isFile()) {
                        processors = readProcessors(file.toPath());
                    } else if (file.isDirectory()) {
                        Path serviceFile = file.toPath()
                                .resolve("META-INF/services/javax.annotation.processing.Processor");
                        if (Files.isRegularFile(serviceFile)) {
                            processors = Files.readAllLines(serviceFile).stream()
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                                    .toList();
                        }
                    }
                } catch (IOException e) {
                    // not an annotation processor
                }
                if (!processors.isEmpty()) {
                    String path = file.getAbsolutePath();
                    moduleDependencies.add(path);
                    annotationProcessorJars.add(path);
                    annotationProcessorNames.addAll(processors);

                    for (Dependency dep : buildContext.getArtifactResolver().getDependencies(artifact)) {
                        if (dep instanceof JarDependency jarDep && jarDep.bytecodeJar() != null) {
                            String depPath = jarDep.bytecodeJar().getAbsolutePath();
                            moduleDependencies.add(depPath);
                            annotationProcessorJars.add(depPath);
                        }
                    }
                }
            }

            if (!annotationProcessorNames.isEmpty()) {
                for (Artifact artifact : project.getDependencyArtifacts()) {
                    if ("provided".equals(artifact.getScope()) || "test".equals(artifact.getScope())) continue;
                    if (!buildContext.getArtifactResolver().isInReactor(artifact)
                            && artifact.getFile() != null && artifact.getFile().isFile()) {
                        String depPath = artifact.getFile().getAbsolutePath();
                        moduleDependencies.add(depPath);
                        annotationProcessorJars.add(depPath);
                    }
                }
            }
        }

        List<AptPath> extraAptPaths = buildContext.getConfig().getExtraAnnotationProcessors();
        for (AptPath aptPath : extraAptPaths) {
            moduleDependencies.add(aptPath.annotationProcessorFile().getAbsolutePath());
            annotationProcessorJars.add(aptPath.annotationProcessorFile().getAbsolutePath());
            annotationProcessorNames.addAll(aptPath.annotationProcessorName());
        }

        if (dependency instanceof ReactorDependency reactor) {
            MavenResourceInputs.prepare(reactor.getMavenProject(), reactor instanceof TestReactorDependency,
                    buildContext.getArtifactResolver() == null ? null : buildContext.getArtifactResolver().getMavenSession(),
                    getOutputDirectory().resolve(APT_RESOURCES));
        }
        if (!annotationProcessorNames.isEmpty()) {
            List<String> resourcePaths = new ArrayList<>();
            if (dependency instanceof ReactorDependency) resourcePaths.add(getOutputDirectory().resolve(APT_RESOURCES).toString());
            for (Dependency dep : dependencies) {
                if (dep instanceof ReactorDependency) {
                    for (Path path : input(dep, OutputTypes.BYTECODE).paths()) {
                        resourcePaths.add(path.getParent().resolve(APT_RESOURCES).toString());
                        resourcePaths.add(path.toString());
                    }
                } else if (dep instanceof JarDependency jar && jar.bytecodeJar() != null) {
                    resourcePaths.add(jar.bytecodeJar().getAbsolutePath());
                }
            }
            if (dependency instanceof ReactorDependency reactor) {
                for (MavenProject project : reactor.getResourceProjects()) {
                    if (project == reactor.getMavenProject() || dependencies.stream()
                            .filter(ReactorDependency.class::isInstance).map(ReactorDependency.class::cast)
                            .anyMatch(dep -> dep.getMavenProject().equals(project))) continue;
                    Path prepared = getOutputDirectory().resolve("apt-dependencies").resolve(project.getGroupId() + "-" + project.getArtifactId());
                    MavenResourceInputs.prepare(project, false, buildContext.getArtifactResolver().getMavenSession(), prepared);
                    resourcePaths.add(prepared.toString());
                }
                for (Artifact artifact : reactor.getResourceArtifacts()) {
                    if (artifact.getFile() != null && artifact.getFile().exists()
                            && !(buildContext.getArtifactResolver() != null
                            && buildContext.getArtifactResolver().isInReactor(artifact) && !artifact.hasClassifier()
                            && "jar".equals(artifact.getType()))) {
                        resourcePaths.add(artifact.getFile().getAbsolutePath());
                    }
                }
            }
            annotationProcessorJars.addAll(0, resourcePaths.stream().distinct().toList());
        }

        List<String> javacOpts = new ArrayList<>();
        buildContext.getConfig().annotationProcessorsArgs().forEach((key, value) ->
                javacOpts.add("-A" + key + "=" + value));

        doCompile(sources, moduleDependencies, annotationProcessorJars,
                annotationProcessorNames, javacOpts, output);

        buildContext.addXtbSearchPath(outputPath());
    }

    protected void doCompile(List<String> sources, Set<String> moduleDependencies,
                             List<String> annotationProcessorJars,
                             List<String> annotationProcessorNames,
                             List<String> javacOpts, File output) {
        compileWithJavac(sources, annotationProcessorJars,
                annotationProcessorNames, javacOpts, output);
    }

    protected void compileWithJavac(List<String> sources,
                                    List<String> annotationProcessorJars,
                                    List<String> annotationProcessorNames,
                                    List<String> javacOpts, File output) {
        Set<String> classpathSet = new LinkedHashSet<>();

        buildContext.getConfig().getExtraClasspath().forEach(f -> classpathSet.add(f.toString()));

        for (Dependency dep : getFlattenDependencies()) {
            if (dep instanceof JarDependency jarDep && jarDep.bytecodeJar() != null) {
                classpathSet.add(jarDep.bytecodeJar().getAbsolutePath());
            } else {
                input(dep, OutputTypes.BYTECODE).filter(TURBINE_OUTPUT).files()
                        .forEach(f -> classpathSet.add(f.getAbsolutePath().toString()));
            }
        }

        annotationProcessorJars.forEach(classpathSet::add);
        Set<String> processorClasspath = new LinkedHashSet<>(annotationProcessorJars);
        processorClasspath.addAll(classpathSet);

        if (dependency.isSourceMapped()) {
            MavenProject project = ((ReactorDependency) dependency).getMavenProject();
            for (Artifact artifact : project.getDependencyArtifacts()) {
                if ("test".equals(artifact.getScope())) continue;
                if (artifact.getFile() != null) {
                    classpathSet.add(artifact.getFile().getAbsolutePath());
                }
            }
        }

        List<File> classpath = classpathSet.stream().map(File::new).toList();

        List<File> sourcePaths = List.of(getOutputDirectory().resolve(APT_RESOURCES).toFile(),
                getOutputDirectory().resolve(OutputTypes.STRIPPED_SOURCES.getName()).toFile());

        File bootstrapClasspath = buildContext.getConfig().getBootstrapClasspath();
        File classOutputDir = outputPath().resolve("classes").toFile();
        classOutputDir.mkdirs();

        try {
            Javac javac = new Javac(logger, outputPath().toFile(), sourcePaths,
                    classpath, classOutputDir, bootstrapClasspath,
                    annotationProcessorNames, javacOpts,
                    processorClasspath.stream().map(File::new).toList());

            if (!javac.compile(sources)) {
                throw new RuntimeException(
                        "Javac compilation failed at dependency " + dependency.key());
            }

            createOutputJar(classOutputDir.toPath(), output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void createOutputJar(Path classesDir, File outputJar) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputJar))) {
            Files.walk(classesDir)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        String entryName = classesDir.relativize(p).toString()
                                .replace(File.separatorChar, '/');
                        try {
                            jos.putNextEntry(new JarEntry(entryName));
                            Files.copy(p, jos);
                            jos.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }
}
