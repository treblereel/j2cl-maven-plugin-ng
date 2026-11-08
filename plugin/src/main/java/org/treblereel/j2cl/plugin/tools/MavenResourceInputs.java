package org.treblereel.j2cl.plugin.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.DefaultMavenFileFilter;
import org.apache.maven.shared.filtering.DefaultMavenResourcesFiltering;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;
import org.sonatype.plexus.build.incremental.DefaultBuildContext;

import static org.treblereel.j2cl.plugin.utils.FileUtils.deleteDirectoryRecursive;

/** Prepares resource inputs without depending on process-resources having run. */
public final class MavenResourceInputs {
    private static final String PLUGIN = "org.apache.maven.plugins:maven-resources-plugin";

    private MavenResourceInputs() {
    }

    public static void prepare(MavenProject project, boolean tests, MavenSession session, Path output) {
        try {
            deleteDirectoryRecursive(output);
            Files.createDirectories(output);
            copy(project, false, session, output);
            if (tests) copy(project, true, session, output);
        } catch (IOException | MavenFilteringException e) {
            throw new IllegalStateException("Unable to prepare APT resources for " + project.getId(), e);
        }
    }

    private static void copy(MavenProject project, boolean tests, MavenSession session, Path output)
            throws MavenFilteringException {
        List<Resource> resources = tests ? project.getTestResources() : project.getResources();
        if (resources.isEmpty()) return;
        Xpp3Dom config = configuration(project, tests);
        String encoding = value(config, "encoding", project.getProperties().getProperty("project.build.sourceEncoding"), project, session);
        MavenResourcesExecution request = new MavenResourcesExecution(resources, output.toFile(), project,
                encoding, values(config, "filters", project, session),
                values(config, "nonFilteredFileExtensions", project, session), session);
        request.setResourcesBaseDirectory(project.getBasedir());
        request.setInjectProjectBuildFilters(flag(config, "useBuildFilters", true, project, session));
        request.setPropertiesEncoding(value(config, "propertiesEncoding", encoding, project, session));
        request.setAddDefaultExcludes(flag(config, "addDefaultExcludes", true, project, session));
        request.setEscapeString(value(config, "escapeString", null, project, session));
        request.setEscapeWindowsPaths(flag(config, "escapeWindowsPaths", true, project, session));
        request.setSupportMultiLineFiltering(flag(config, "supportMultiLineFiltering", false, project, session));
        request.setFilterFilenames(flag(config, "fileNameFiltering", false, project, session));
        request.setDelimiters(new LinkedHashSet<>(values(config, "delimiters", project, session)),
                flag(config, "useDefaultDelimiters", true, project, session));
        // Test resources overwrite matching main-resource paths in the isolated APT input.
        request.setOverwrite(true);
        DefaultBuildContext context = new DefaultBuildContext();
        new DefaultMavenResourcesFiltering(new DefaultMavenFileFilter(context), context).filterResources(request);
    }

    public static Xpp3Dom configuration(MavenProject project, boolean tests) {
        Plugin plugin = project.getPlugin(PLUGIN);
        if (plugin == null && project.getBuild().getPluginManagement() != null) {
            plugin = project.getBuild().getPluginManagement().getPluginsAsMap().get(PLUGIN);
        }
        Xpp3Dom config = plugin == null || plugin.getConfiguration() == null
                ? new Xpp3Dom("configuration") : new Xpp3Dom((Xpp3Dom) plugin.getConfiguration());
        if (plugin != null) {
            String goal = tests ? "testResources" : "resources";
            String executionId = tests ? "default-testResources" : "default-resources";
            for (var execution : plugin.getExecutions()) {
                if ((executionId.equals(execution.getId()) || execution.getGoals().contains(goal))
                        && execution.getConfiguration() != null) {
                    config = Xpp3DomUtils.mergeXpp3Dom(new Xpp3Dom((Xpp3Dom) execution.getConfiguration()), config);
                }
            }
        }
        return config;
    }

    public static List<Path> filterPaths(MavenProject project, boolean tests, MavenSession session) {
        List<Path> paths = new ArrayList<>();
        for (boolean test : tests ? List.of(false, true) : List.of(false)) {
            Xpp3Dom config = configuration(project, test);
            List<String> filters = new ArrayList<>(values(config, "filters", project, session));
            if (flag(config, "useBuildFilters", true, project, session)) filters.addAll(project.getBuild().getFilters());
            for (String filter : filters) {
                Path path = Path.of(filter);
                paths.add(path.isAbsolute() ? path : project.getBasedir().toPath().resolve(path));
            }
        }
        return paths;
    }

    public static String fingerprint(MavenProject project, boolean tests, MavenSession session) {
        Map<String, Object> settings = new TreeMap<>();
        // Rebuild old bytecode trees that mixed resource inputs with generated sources.
        settings.put("format", 2);
        settings.put("main", project.getResources());
        settings.put("mainConfiguration", configuration(project, false).toString());
        if (tests) {
            settings.put("test", project.getTestResources());
            settings.put("testConfiguration", configuration(project, true).toString());
        }
        settings.put("filters", project.getBuild().getFilters());
        settings.put("properties", new TreeMap<>(project.getProperties()));
        settings.put("project", List.of(project.getId(), String.valueOf(project.getName()),
                String.valueOf(project.getDescription()), String.valueOf(project.getBasedir())));
        if (session != null) {
            settings.put("userProperties", new TreeMap<>(session.getUserProperties()));
            settings.put("systemProperties", new TreeMap<>(session.getSystemProperties()));
        }
        return new Gson().toJson(settings);
    }

    private static boolean flag(Xpp3Dom config, String name, boolean fallback, MavenProject project, MavenSession session) {
        return Boolean.parseBoolean(value(config, name, Boolean.toString(fallback), project, session));
    }

    private static List<String> values(Xpp3Dom config, String name, MavenProject project, MavenSession session) {
        Xpp3Dom child = config.getChild(name);
        if (child == null) return List.of();
        List<String> result = new ArrayList<>();
        for (Xpp3Dom item : child.getChildren()) result.add(evaluate(item.getValue(), project, session));
        return result;
    }

    private static String value(Xpp3Dom config, String name, String fallback, MavenProject project, MavenSession session) {
        Xpp3Dom child = config.getChild(name);
        return child == null ? fallback : evaluate(child.getValue(), project, session);
    }

    private static String evaluate(String value, MavenProject project, MavenSession session) {
        if (value == null || session == null || !value.contains("${")) return value;
        MavenSession copy = session.clone();
        copy.setCurrentProject(project);
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-resources-plugin");
        try {
            return String.valueOf(new PluginParameterExpressionEvaluator(copy,
                    new MojoExecution(plugin, "resources", "default-resources")).evaluate(value));
        } catch (org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException e) {
            throw new IllegalArgumentException("Cannot evaluate resource setting " + value, e);
        }
    }
}
