package org.example.tools;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;
import org.example.context.ArtifactResolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class APTProcessors {

  private static final String COMPILER_GA = "org.apache.maven.plugins:maven-compiler-plugin";

  public static List<AptPath> getAPTProcessorPaths(MavenProject project, ArtifactResolver artifactResolver, PluginParameterExpressionEvaluator evaluator) {

    Plugin plugin = project.getPlugin(COMPILER_GA);

    if (plugin == null) return List.of();

    Xpp3Dom mavenCompilerConfig = getMergedCompilerConfig(project, plugin);
    Xpp3Dom aps = mavenCompilerConfig.getChild("annotationProcessorPaths");
    if (aps == null) return List.of();

    List<AptPath> result = new ArrayList<>();

    for (Xpp3Dom path : aps.getChildren("path")) {
      String g = evalStr(evaluator, childValue(path, "groupId"));
      String a = evalStr(evaluator, childValue(path, "artifactId"));
      String v = evalStr(evaluator, childValue(path, "version"));
      String c = evalStr(evaluator, childValue(path, "classifier"));
      String t = evalStr(evaluator, childValue(path, "type"));

      String coords = g + ":" + a + ":" + v;

      try {
        File jar = artifactResolver.getJarWithMavenCoords(coords);
        result.add(new AptPath(jar, new ArrayList<>(ServiceFileReader.readProcessors(jar.toPath()))));
      } catch (MojoExecutionException | IOException e) {
        throw new RuntimeException(e);
      }

    }
    return result;
  }


  private static Xpp3Dom getMergedCompilerConfig(MavenProject project, Plugin plugin) {
    Xpp3Dom base = (Xpp3Dom) plugin.getConfiguration();
    Xpp3Dom merged = base != null ? new Xpp3Dom(base) : new Xpp3Dom("configuration");

    for (PluginExecution exec : plugin.getExecutions()) {
      Xpp3Dom execCfg = (Xpp3Dom) exec.getConfiguration();
      if (execCfg != null) {
        merged = Xpp3DomUtils.mergeXpp3Dom(new Xpp3Dom(execCfg), merged);
      }
    }

    if (project.getBuild() != null
            && project.getBuild().getPluginManagement() != null
            && project.getBuild().getPluginManagement().getPluginsAsMap().containsKey(COMPILER_GA)) {
      Plugin pm = project.getBuild().getPluginManagement().getPluginsAsMap().get(COMPILER_GA);
      Xpp3Dom pmCfg = (Xpp3Dom) pm.getConfiguration();
      if (pmCfg != null) {
        merged = Xpp3DomUtils.mergeXpp3Dom(merged, new Xpp3Dom(pmCfg));
      }
    }

    return merged;
  }

  private static String childValue(Xpp3Dom dom, String name) {
    if (dom == null) return null;
    Xpp3Dom c = dom.getChild(name);
    return c != null ? c.getValue() : null;
  }

  private static String evalStr(PluginParameterExpressionEvaluator eval, String s) {
    if (s == null) return null;
    if (eval == null) return s;
    try {
      Object v = eval.evaluate(s);
      return v != null ? String.valueOf(v) : null;
    } catch (Exception e) {
      return s;
    }
  }

}
