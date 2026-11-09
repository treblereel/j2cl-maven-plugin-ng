package org.treblereel.j2cl.plugin.utils;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.javascript.jscomp.SourceFile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileUtilsJsZipTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "ISO-8859-1"})
    void retainsNamesCharsetAndCodeAfterArchiveReplacementAndDeletion(String encoding) throws Exception {
        Charset charset = Charset.forName(encoding);
        Path archive = directory.resolve("runtime with spaces.zip");
        String code = "const message = 'français';";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive), charset)) {
            for (String name : List.of("nested/", "nested/main.js", "ignored.txt")) {
                zip.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) zip.write(code.getBytes(charset));
                zip.closeEntry();
            }
        }
        List<SourceFile> expected = SourceFile.fromZipFile(archive.toString(), charset);
        List<SourceFile> sources = FileUtils.readJsZip(archive, charset);
        assertEquals(1, sources.size());
        assertEquals(expected.getFirst().getName(), sources.getFirst().getName());
        assertEquals(expected.getFirst().getOriginalPath(), sources.getFirst().getOriginalPath());
        Files.writeString(archive, "replaced");
        Files.delete(archive);
        assertEquals(code, sources.getFirst().getCode());
        sources.getFirst().clearCachedSource();
        assertEquals(code, sources.getFirst().getCode());
    }
}
