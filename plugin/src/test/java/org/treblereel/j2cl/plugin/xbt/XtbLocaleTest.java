package org.treblereel.j2cl.plugin.xbt;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XtbLocaleTest {
    @TempDir
    Path directory;

    @Test
    void autoDiscoveryUsesExactBundleLocaleRatherThanFilename() throws Exception {
        Path french = Files.writeString(directory.resolve("messages_fr.xtb"), bundle("fr", "Bonjour"));
        Path regional = Files.writeString(directory.resolve("messages_fr_nr.xtb"), bundle("fr-nr", "NR"));
        Files.writeString(directory.resolve("generated_messages.xtb"), bundle("fr", "stale output"));
        assertEquals(List.of(french.toFile()),
                XtbResolver.findAllXtbByLocale("fr", directory.toFile(), List.of(), null));
        assertEquals(List.of(regional.toFile()),
                XtbResolver.findAllXtbByLocale("fr_nr", directory.toFile(), List.of(), null));
    }

    @Test
    void mismatchedSingleBundleDoesNotSilentlyBecomeFallback() {
        assertThrows(IllegalArgumentException.class,
                () -> XtbPreprocessor.preprocess(input(bundle("fr-nr", "NR")), "fr"));
    }

    @Test
    void mismatchedMultipleBundlesDoNotFallBackToFirst() {
        assertThrows(IllegalArgumentException.class, () -> XtbPreprocessor.preprocess(
                input("<root>" + bundle("en", "Hello") + bundle("fr-nr", "NR") + "</root>"), "fr"));
    }

    @Test
    void mergeCannotOverwriteFrenchWithRegionalTranslations() throws Exception {
        for (List<String> bundles : List.of(List.of(bundle("fr", "Bonjour"), bundle("fr-nr", "NR")),
                List.of(bundle("fr-nr", "NR"), bundle("fr", "Bonjour")))) {
            try (var merged = XtbPreprocessor.merge(bundles.stream().map(XtbLocaleTest::input)
                    .map(stream -> (java.io.InputStream) stream).toList(), "fr")) {
                String text = new String(merged.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(text.contains("Bonjour"));
                assertFalse(text.contains(">NR<"));
            }
        }
    }

    @Test
    void normalizesHyphensAndUnderscoresInBothDirections() throws Exception {
        for (String locale : List.of("fr-nr", "fr_nr")) {
            for (String lang : List.of("fr-nr", "fr_nr")) {
                try (var result = XtbPreprocessor.preprocess(input(bundle(lang, "NR")), locale)) {
                    assertTrue(new String(result.readAllBytes(), StandardCharsets.UTF_8).contains(">NR<"));
                }
            }
        }
    }

    private static String bundle(String locale, String message) {
        return "<translationbundle lang=\"" + locale + "\"><translation id=\"1\">"
                + message + "</translation></translationbundle>";
    }

    private static ByteArrayInputStream input(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
