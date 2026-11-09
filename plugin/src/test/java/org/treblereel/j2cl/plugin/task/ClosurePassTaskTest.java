package org.treblereel.j2cl.plugin.task;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.config.BuildConfig;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.Dependency;
import org.treblereel.j2cl.plugin.model.ReactorDependency;
import org.treblereel.j2cl.plugin.xbt.TranslationsFileConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosurePassTaskTest {
    @TempDir
    Path directory;

    @Test
    void bundleTranslationPassActuallyReplacesMessagesWithoutResolvingOtherModules() throws Exception {
        Path source = Files.createDirectory(directory.resolve("input"));
        Files.writeString(source.resolve("Messages.js"), """
                goog.module('example.Messages');
                const Other = goog.require('example.Other');
                /** @desc Button label prompting the user to click */
                var MSG_CLICK_ME = goog.getMsg('Click me!');
                exports = {label: MSG_CLICK_ME};
                """);
        Path xtb = Files.writeString(directory.resolve("messages_fr.xtb"), """
                <translationbundle lang="fr">
                    <translation id="1604231182115774751">Cliquez ici !</translation>
                </translationbundle>
                """);
        TranslationsFileConfig translations = new TranslationsFileConfig();
        translations.setFile(xtb.toString());
        MavenProject project = new MavenProject();
        project.setFile(directory.resolve("pom.xml").toFile());
        Build build = new Build();
        build.setDirectory(directory.resolve("target").toString());
        project.setBuild(build);
        var dependency = new ReactorDependency(project, null);
        BuildConfig config = new BuildConfig(List.of(), List.of(), null, "app.js", directory.resolve("web").toString(),
                "BUNDLE_JAR", Map.of("goog.LOCALE", "fr"), false, translations, false, "ECMASCRIPT_2017", true, "BROWSER", Map.of());
        try (BuildContext context = new BuildContext(project, config, null, null)) {
            new ClosurePassTask(dependency, context, LOG) {
                protected TaskOutput input(Collection<Dependency> dependencies, OutputTypes type) {
                    return new TaskOutput(List.of(source));
                }
            }.process();
            Path output = context.getOutputDirectory().resolve(dependency.key()).resolve("closure_pass/Messages.js");
            String translated = Files.readString(output);
            assertTrue(translated.contains("Cliquez ici !"), translated);
            assertFalse(translated.contains("goog.getMsg"), translated);
            assertTrue(translated.contains("goog.module"), translated);
            assertTrue(translated.contains("goog.require"), translated);
        }
    }

    private static final BuildLog LOG = (BuildLog) Proxy.newProxyInstance(BuildLog.class.getClassLoader(),
            new Class<?>[]{BuildLog.class}, (proxy, method, args) -> null);
}
