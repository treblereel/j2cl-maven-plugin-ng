package org.treblereel.j2cl.plugin.utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.j2cl.plugin.model.JarDependency;
import org.treblereel.j2cl.plugin.task.BundleJarTask;
import org.treblereel.j2cl.plugin.task.FinalTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsExtractionTest {

    @TempDir
    Path directory;

    @Test
    void extractsNestedFilesDirectoriesAndReplacesFiles() throws Exception {
        Path root = Files.createDirectory(directory.resolve("cache"));
        Files.writeString(root.resolve("existing.txt"), "old contents");
        extract(archive("./", "empty/", "pkg/Test.java", "existing.txt"), root);
        assertTrue(Files.isDirectory(root.resolve("empty")));
        assertEquals("contents", Files.readString(root.resolve("pkg/Test.java")));
        assertEquals("contents", Files.readString(root.resolve("existing.txt")));
    }

    @Test
    void rejectsTraversalAbsoluteAndWindowsPaths() throws Exception {
        Path root = directory.resolve("cache");
        Path outside = directory.resolve("outside.txt");
        Files.writeString(outside, "untouched");
        for (String name : new String[]{"../outside.txt", "pkg/../../outside.txt",
                "../cache-other/outside.txt", outside.toString(), "C:/outside.txt",
                "C:outside.txt", "..\\outside.txt", "\\\\host\\share\\file", "bad\u0000name", "."}) {
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> extract(archive(name), root), name);
            assertTrue(error.getCause() instanceof FileUtils.UnsafeArchiveEntryException, name);
            assertEquals("untouched", Files.readString(outside), name);
        }
        assertFalse(Files.exists(directory.resolve("cache-other")));
    }

    @Test
    void rejectsEscapingDirectoryEntryWithoutCreatingIt() throws Exception {
        assertThrows(RuntimeException.class,
                () -> extract(archive("../outside/"), directory.resolve("cache")));
        assertFalse(Files.exists(directory.resolve("outside")));
    }

    @Test
    void rejectsExistingDirectoryAndFileSymlinks() throws Exception {
        Path root = Files.createDirectory(directory.resolve("cache"));
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Path secret = Files.writeString(outside.resolve("secret.txt"), "untouched");
        Files.createSymbolicLink(root.resolve("escape"), outside);
        Files.createSymbolicLink(root.resolve("alias.txt"), secret);
        assertThrows(RuntimeException.class, () -> extract(archive("escape/new.txt"), root));
        assertThrows(RuntimeException.class, () -> extract(archive("alias.txt"), root));
        assertFalse(Files.exists(outside.resolve("new.txt")));
        assertEquals("untouched", Files.readString(secret));
    }

    @Test
    void rejectsOriginalTraversalEvenAfterPrefixWasStripped() throws Exception {
        Path jar = archive("pkg/../../outside.js");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.entries().nextElement();
            assertThrows(FileUtils.UnsafeArchiveEntryException.class,
                    () -> FileUtils.extractZipEntry(zip, entry, directory.resolve("cache"), "outside.js"));
        }
        assertFalse(Files.exists(directory.resolve("cache")));
    }

    @Test
    void validatesTransformedPathAndPreservesNormalPrefixStripping() throws Exception {
        Path root = directory.resolve("cache");
        Path jar = archive("pkg/good.js");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.entries().nextElement();
            assertThrows(FileUtils.UnsafeArchiveEntryException.class,
                    () -> FileUtils.extractZipEntry(zip, entry, root, "../outside.js"));
            FileUtils.extractZipEntry(zip, entry, root, "good.js");
        }
        assertEquals("contents", Files.readString(root.resolve("good.js")));
        assertFalse(Files.exists(directory.resolve("outside.js")));
    }

    private Path archive(String... names) throws IOException {
        Path jar = Files.createTempFile(directory, "input-", ".jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) zip.write("contents".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void sourceMapExtractorsRejectTraversalAfterCommonPrefix() throws Exception {
        Path jar = archive("pkg/good.js", "pkg/../../outside.js");
        for (Object task : new Object[]{new FinalTask(null, null, null), new BundleJarTask(null, null, null)}) {
            var method = task.getClass().getDeclaredMethod("extractZipSources", File.class, Path.class);
            method.setAccessible(true);
            InvocationTargetException error = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(task, jar.toFile(), directory.resolve("cache")));
            assertTrue(error.getCause() instanceof FileUtils.UnsafeArchiveEntryException);
        }
        assertFalse(Files.exists(directory.resolve("outside.js")));
    }

    @Test
    void sourceMapExtractorsPreserveCommonPrefixLayout() throws Exception {
        Path jar = archive("pkg/good.js", "pkg/nested/other.js");
        for (Object task : new Object[]{new FinalTask(null, null, null), new BundleJarTask(null, null, null)}) {
            Path root = directory.resolve(task.getClass().getSimpleName());
            var method = task.getClass().getDeclaredMethod("extractZipSources", File.class, Path.class);
            method.setAccessible(true);
            method.invoke(task, jar.toFile(), root);
            assertEquals("contents", Files.readString(root.resolve("good.js")));
            assertEquals("contents", Files.readString(root.resolve("nested/other.js")));
        }
    }

    private void extract(Path jar, Path root) {
        JarDependency dependency = new JarDependency(new DefaultArtifact(
                "test", "archive", "1", "compile", "jar", null, new DefaultArtifactHandler()), null);
        FileUtils.extractZip(jar.toFile(), root, dependency);
    }
}
