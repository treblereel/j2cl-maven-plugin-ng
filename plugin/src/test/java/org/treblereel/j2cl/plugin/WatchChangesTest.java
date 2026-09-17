package org.treblereel.j2cl.plugin;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.treblereel.j2cl.plugin.context.BuildContext;
import org.treblereel.j2cl.plugin.log.BuildLog;
import org.treblereel.j2cl.plugin.model.ReactorDependency;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchChangesTest {

    @Test
    void retainsRepeatedEditOfFileThatTriggeredBuild() throws Exception {
        assertEquals(Set.of("src/A.java"), pendingChanges(List.of("A.java", "A.java"), List.of()));
    }

    @Test
    void retainsRepeatedEditQueuedAtBuildCompletion() throws Exception {
        assertEquals(Set.of("src/A.java"), pendingChanges(List.of(), List.of("A.java")));
    }

    @Test
    void retainsOtherFilesAlongsideRepeatedEdit() throws Exception {
        assertEquals(Set.of("src/A.java", "src/B.java"),
                pendingChanges(List.of("A.java", "B.java"), List.of()));
    }

    @Test
    void unchangedBuildDoesNotScheduleAnotherBuild() throws Exception {
        assertTrue(pendingChanges(List.of(), List.of()).isEmpty());
    }

    private Set<String> pendingChanges(List<String> duringBuild, List<String> afterBuild) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        WatchJ2clPluginMojo mojo = new WatchJ2clPluginMojo() {
            @Override
            void rebuild(ReactorDependency project, BuildContext context, BuildLog log, Set<String> changed) {
                started.countDown();
                try {
                    finish.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        Path source = Path.of("src");
        EventKey during = new EventKey(duringBuild);
        EventKey after = new EventKey(afterBuild);
        var directories = new HashMap<WatchKey, Path>();
        directories.put(during, source);
        directories.put(after, source);
        WatchService watcher = new WatchService() {
            private boolean delivered;
            private final ArrayDeque<WatchKey> finalEvents = new ArrayDeque<>(List.of(after));

            @Override
            public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                finish.countDown();
                if (delivered) return null;
                delivered = true;
                return during;
            }

            @Override
            public WatchKey poll() {
                return finalEvents.poll();
            }

            @Override
            public WatchKey take() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
            }
        };
        try (watcher) {
            return mojo.rebuildAndMonitor(null, null, null, Set.of(source.resolve("A.java").toString()),
                    watcher, directories).stream().map(path -> path.replace('\\', '/'))
                    .collect(java.util.stream.Collectors.toSet());
        } finally {
            finish.countDown();
        }
    }

    private static class EventKey implements WatchKey {
        private final List<WatchEvent<?>> events;

        EventKey(List<String> names) {
            events = names.stream().<WatchEvent<?>>map(name -> new WatchEvent<Path>() {
                public Kind<Path> kind() { return ENTRY_MODIFY; }
                public int count() { return 1; }
                public Path context() { return Path.of(name); }
            }).toList();
        }

        public boolean isValid() { return true; }
        public List<WatchEvent<?>> pollEvents() { return events; }
        public boolean reset() { return true; }
        public void cancel() { }
        public Watchable watchable() { return Path.of("src"); }
    }
}
