package org.treblereel.j2cl.plugin.xbt;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
class XtbPreprocessorTest {

    @Nested
    class EscapeTest {

        @Test
        void ampersandEscaped() {
            assertEquals("&amp;", XtbPreprocessor.escape("&"));
        }

        @Test
        void lessThanDoubleEscaped() {
            // & is escaped AFTER < intentionally: getInnerContent() already
            // produces &lt; from the DOM Transformer, so the second pass
            // must preserve entity references as literal text in XML output.
            assertEquals("&amp;lt;", XtbPreprocessor.escape("<"));
        }

        @Test
        void greaterThanDoubleEscaped() {
            assertEquals("&amp;gt;", XtbPreprocessor.escape(">"));
        }

        @Test
        void quoteDoubleEscaped() {
            assertEquals("&amp;quot;", XtbPreprocessor.escape("\""));
        }

        @Test
        void apostropheNotEscaped() {
            assertEquals("'", XtbPreprocessor.escape("'"));
        }

        @Test
        void emptyString() {
            assertEquals("", XtbPreprocessor.escape(""));
        }

        @Test
        void noSpecialChars() {
            assertEquals("hello world", XtbPreprocessor.escape("hello world"));
        }
    }

    @Nested
    class ParseTest {

        @Test
        void textWithPhSelfClosing() {
            String content = "Au revoir <ph name=\"arg\" /> !";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(3, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void phSelfClosingOnly() {
            String content = "<ph name=\"arg\" />";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(1, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void phWithClosingTag() {
            String content = "<ph name=\"arg\" ></ph>";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(1, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void phWithInnerText() {
            String content = "<ph name=\"arg\" >QWERTY</ph>";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(1, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void twoPhWithClosingTags() {
            String content = "<ph name=\"arg\" >QWERTY</ph><ph name=\"arg\" >QWERTY</ph>";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(2, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void phClosingThenSelfClosing() {
            String content = "<ph name=\"arg\" >QWERTY</ph><ph name=\"arg\" />";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(2, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void phSelfClosingThenClosing() {
            String content = "<ph name=\"arg\" /><ph name=\"arg\" >QWERTY</ph>";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(2, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void phSelfClosingWithTextBetween() {
            String content = "<ph name=\"arg\" />QWERTY<ph name=\"arg\" >QWERTY</ph>";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(3, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void textBeforeBothPh() {
            String content = "QWERTY<ph name=\"arg\" />QWERTY<ph name=\"arg\" >QWERTY</ph>";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(4, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void htmlWithPhSelfClosing() {
            String content = "<div id='WOW'><ph name=\"arg\" /></div>";
            String escaped = "&amp;lt;div id='WOW'&amp;gt;"
                    + "<ph name=\"arg\" />&amp;lt;/div&amp;gt;";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(3, parts.size());
            assertEquals(escaped, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void htmlWithPhClosingTag() {
            String content = "<div id='WOW'><ph name=\"arg\" >QWERTY</ph></div>";
            String escaped = "&amp;lt;div id='WOW'&amp;gt;"
                    + "<ph name=\"arg\" >QWERTY</ph>&amp;lt;/div&amp;gt;";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(3, parts.size());
            assertEquals(escaped, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void nestedDivsNoPlaceholders() {
            String content = "<div id=\"div1\"><div id=\"div2\">"
                    + "<div id=\"div3\"></div><div id=\"div4\"></div></div></div>";
            String escaped = "&amp;lt;div id=&amp;quot;div1&amp;quot;&amp;gt;"
                    + "&amp;lt;div id=&amp;quot;div2&amp;quot;&amp;gt;"
                    + "&amp;lt;div id=&amp;quot;div3&amp;quot;&amp;gt;&amp;lt;/div&amp;gt;"
                    + "&amp;lt;div id=&amp;quot;div4&amp;quot;&amp;gt;&amp;lt;/div&amp;gt;"
                    + "&amp;lt;/div&amp;gt;&amp;lt;/div&amp;gt;";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(1, parts.size());
            assertEquals(escaped, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void simpleHtml() {
            String content = "<div>WOW</div>";
            String escaped = "&amp;lt;div&amp;gt;WOW&amp;lt;/div&amp;gt;";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(1, parts.size());
            assertEquals(escaped, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void complexNestedHtmlWithPlaceholders() {
            String content = "<div id=\"div1\"><ph name=\"arg\" >QWERTY</ph>"
                    + "<div id=\"div2\"><div id=\"div3\">RRRRRRR<ph name=\"arg\" /></div>"
                    + "<ph name=\"arg\" /><div id=\"div4\"></div></div></div>"
                    + "<ph name=\"arg\" >QWERTY</ph>";
            String escaped = "&amp;lt;div id=&amp;quot;div1&amp;quot;&amp;gt;"
                    + "<ph name=\"arg\" >QWERTY</ph>"
                    + "&amp;lt;div id=&amp;quot;div2&amp;quot;&amp;gt;"
                    + "&amp;lt;div id=&amp;quot;div3&amp;quot;&amp;gt;RRRRRRR"
                    + "<ph name=\"arg\" />&amp;lt;/div&amp;gt;"
                    + "<ph name=\"arg\" />"
                    + "&amp;lt;div id=&amp;quot;div4&amp;quot;&amp;gt;&amp;lt;/div&amp;gt;"
                    + "&amp;lt;/div&amp;gt;&amp;lt;/div&amp;gt;"
                    + "<ph name=\"arg\" >QWERTY</ph>";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(8, parts.size());
            assertEquals(escaped, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void textBeforePhSelfClosing() {
            String content = "RRRRRRR<ph name=\"arg\" />";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(2, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void textBeforePhClosing() {
            String content = "RRRRRRR<ph name=\"arg\" ></ph>";
            List<String> parts = XtbPreprocessor.parse(content);
            assertEquals(2, parts.size());
            assertEquals(content, parts.stream().collect(Collectors.joining("")));
        }

        @Test
        void emptyString() {
            List<String> parts = XtbPreprocessor.parse("");
            assertTrue(parts.isEmpty());
        }
    }

    @Nested
    class PreprocessTest {

        @Test
        void singleBundle() throws Exception {
            String xtb = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!DOCTYPE translationbundle SYSTEM \"translationbundle.dtd\">\n"
                    + "<translationbundle lang=\"es\">\n"
                    + "  <translation id=\"123\">Hola</translation>\n"
                    + "</translationbundle>\n";

            InputStream result = XtbPreprocessor.preprocess(toStream(xtb));
            String output = streamToString(result);

            assertTrue(output.contains("lang=\"es\""));
            assertTrue(output.contains("<translation id=\"123\">Hola</translation>"));
        }

        @Test
        void selectsBundleByLocale() throws Exception {
            String xtb = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<root>\n"
                    + "<translationbundle lang=\"en\">\n"
                    + "  <translation id=\"1\">Hello</translation>\n"
                    + "</translationbundle>\n"
                    + "<translationbundle lang=\"fr\">\n"
                    + "  <translation id=\"1\">Bonjour</translation>\n"
                    + "</translationbundle>\n"
                    + "</root>\n";

            InputStream result = XtbPreprocessor.preprocess(toStream(xtb), "fr");
            String output = streamToString(result);

            assertTrue(output.contains("lang=\"fr\""));
            assertTrue(output.contains("Bonjour"));
        }

        @Test
        void localeNormalizationDashToUnderscore() throws Exception {
            String xtb = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<root>\n"
                    + "<translationbundle lang=\"pt_BR\">\n"
                    + "  <translation id=\"1\">Ola</translation>\n"
                    + "</translationbundle>\n"
                    + "</root>\n";

            InputStream result = XtbPreprocessor.preprocess(toStream(xtb), "pt-BR");
            String output = streamToString(result);

            assertTrue(output.contains("lang=\"pt_BR\""));
            assertTrue(output.contains("Ola"));
        }

        @Test
        void emptyBundleReturnsEmpty() throws Exception {
            String xtb = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<root></root>\n";

            InputStream result = XtbPreprocessor.preprocess(toStream(xtb));
            String output = streamToString(result);

            assertTrue(output.isEmpty());
        }

        @Test
        void translationWithPlaceholders() throws Exception {
            String xtb = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<translationbundle lang=\"es\">\n"
                    + "  <translation id=\"42\">Hola <ph name=\"USER\"/>!</translation>\n"
                    + "</translationbundle>\n";

            InputStream result = XtbPreprocessor.preprocess(toStream(xtb));
            String output = streamToString(result);

            assertTrue(output.contains("<ph name=\"USER\"/>"));
            assertTrue(output.contains("Hola"));
        }
    }

    @Nested
    class MergeTest {

        @Test
        void mergesTwoFiles() throws Exception {
            String xtb1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<translationbundle lang=\"es\">\n"
                    + "  <translation id=\"1\">Hola</translation>\n"
                    + "</translationbundle>\n";

            String xtb2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<translationbundle lang=\"es\">\n"
                    + "  <translation id=\"2\">Mundo</translation>\n"
                    + "</translationbundle>\n";

            InputStream result = XtbPreprocessor.merge(
                    List.of(toStream(xtb1), toStream(xtb2)), "es");
            String output = streamToString(result);

            assertTrue(output.contains("<translation id=\"1\">Hola</translation>"));
            assertTrue(output.contains("<translation id=\"2\">Mundo</translation>"));
            assertTrue(output.contains("lang=\"es\""));
        }

        @Test
        void mergeSkipsEmptyBundle() throws Exception {
            String xtb1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<translationbundle lang=\"es\">\n"
                    + "  <translation id=\"1\">Hola</translation>\n"
                    + "</translationbundle>\n";

            String xtb2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<root></root>\n";

            InputStream result = XtbPreprocessor.merge(
                    List.of(toStream(xtb1), toStream(xtb2)), "es");
            String output = streamToString(result);

            assertTrue(output.contains("<translation id=\"1\">Hola</translation>"));
        }

        @Test
        void mergeSelectsLocale() throws Exception {
            String xtb = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<root>\n"
                    + "<translationbundle lang=\"en\">\n"
                    + "  <translation id=\"1\">Hello</translation>\n"
                    + "</translationbundle>\n"
                    + "<translationbundle lang=\"de\">\n"
                    + "  <translation id=\"1\">Hallo</translation>\n"
                    + "</translationbundle>\n"
                    + "</root>\n";

            InputStream result = XtbPreprocessor.merge(
                    List.of(toStream(xtb)), "de");
            String output = streamToString(result);

            assertTrue(output.contains("Hallo"));
        }
    }

    private static InputStream toStream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String streamToString(InputStream is) throws Exception {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
