package dev.drunkshyt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CounterStoreTest {
    @TempDir Path directory;

    @Test void restartRestoresIndependentCountersAndFeedbackPreferences() throws IOException {
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        CounterStore before = new CounterStore(directory);
        before.save(one, new PlayerRecord("One", new ShotCount(10, 7), false));
        before.save(two, new PlayerRecord("Two", new ShotCount(4, 0), true));
        var after = new CounterStore(directory).load();
        assertEquals(new PlayerRecord("One", new ShotCount(10, 7), false), after.get(one));
        assertEquals(new PlayerRecord("Two", new ShotCount(4, 0), true), after.get(two));
    }

    @Test void replacementPersistsResetAndUpdatedNameWithoutLeftoverTemporaryFiles() throws IOException {
        UUID id = UUID.randomUUID();
        CounterStore store = new CounterStore(directory);
        store.save(id, new PlayerRecord("Before", new ShotCount(10, 7), false));
        store.save(id, new PlayerRecord("After", ShotCount.ZERO, false));
        assertEquals(new PlayerRecord("After", ShotCount.ZERO, false), store.load().get(id));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test void corruptedCounterIsPreservedAndNeverSilentlyReset() throws IOException {
        Path file = directory.resolve(UUID.randomUUID() + ".properties");
        String damaged = "name=One\ntotal=4\ncompleted=99\nfeedback=true\n";
        Files.writeString(file, damaged);
        assertThrows(IOException.class, () -> new CounterStore(directory).load());
        assertEquals(damaged, Files.readString(file));
    }

    @Test void leftoverInterruptedWriteDoesNotOverrideLastCompleteRecord() throws IOException {
        UUID id = UUID.randomUUID();
        CounterStore store = new CounterStore(directory);
        store.save(id, new PlayerRecord("One", new ShotCount(10, 7), true));
        Files.writeString(directory.resolve("counter-interrupted.tmp"), "total=123");
        assertEquals(new ShotCount(10, 7), store.load().get(id).count());
    }
}
