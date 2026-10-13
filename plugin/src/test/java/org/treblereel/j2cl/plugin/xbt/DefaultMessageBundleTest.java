package org.treblereel.j2cl.plugin.xbt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMessageBundleTest {
    @TempDir
    Path directory;

    @Test
    void advancedWithoutXtbReplacesPlaceholdersBeforeRenaming() {
        CompilerOptions options = options();
        XtbResolver.applyTranslations(options, null, Map.of(), directory.toFile(), List.of(), null);
        String output = compile(options);
        assertTrue(output.contains("Good bye Billy !"), output);
        assertFalse(output.contains("{$arg}"), output);
    }

    @Test
    void configuredXtbReplacesEmptyDefaultBundle() throws Exception {
        Path xtb = Files.writeString(directory.resolve("messages_fr.xtb"),
                "<translationbundle lang=\"fr\"><translation id=\"1\">Bonjour</translation></translationbundle>");
        CompilerOptions options = options();
        XtbResolver.applyXtbFiles(options, List.of(xtb.toFile()), "fr", null);
        assertTrue(options.getMessageBundle() instanceof com.google.javascript.jscomp.XtbMessageBundle);
    }

    @Test
    void simpleDoesNotEnableMessageReplacement() {
        CompilerOptions options = new CompilerOptions();
        var previous = options.getMessageBundle();
        XtbResolver.applyDefaultMessageBundle(options, CompilationLevel.SIMPLE_OPTIMIZATIONS);
        org.junit.jupiter.api.Assertions.assertSame(previous, options.getMessageBundle());
    }

    private CompilerOptions options() {
        CompilerOptions options = new CompilerOptions();
        CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        options.setClosurePass(true);
        XtbResolver.applyDefaultMessageBundle(options, CompilationLevel.ADVANCED_OPTIMIZATIONS);
        return options;
    }

    private String compile(CompilerOptions options) {
        Compiler compiler = new Compiler();
        var result = compiler.compile(List.of(SourceFile.fromCode("externs.js", "var window;")),
                List.of(SourceFile.fromCode("test.js", """
                        var goog = {};
                        goog.getMsg = function(message, values) { return message; };
                        /** @desc Greeting */
                        var MSG_GREETING = goog.getMsg('Good bye {$arg} !', {arg: 'Billy'});
                        window['result'] = MSG_GREETING;
                        """)), options);
        assertTrue(result.success, result.errors.toString());
        return compiler.toSource();
    }
}
