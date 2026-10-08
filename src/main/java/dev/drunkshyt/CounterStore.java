package dev.drunkshyt;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** One file per player, replaced only after the new record has been written. */
public final class CounterStore {
    private final Path directory;

    public CounterStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
    }

    public Map<UUID, PlayerRecord> load() throws IOException {
        Map<UUID, PlayerRecord> records = new HashMap<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".properties")).toList()) {
                Properties data = new Properties();
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { data.load(reader); }
                try {
                    String file = path.getFileName().toString();
                    UUID id = UUID.fromString(file.substring(0, file.length() - ".properties".length()));
                    String name = data.getProperty("name");
                    if (name == null || name.isBlank()) throw new IllegalArgumentException("Missing name");
                    ShotCount count = new ShotCount(Integer.parseInt(data.getProperty("total")),
                            Integer.parseInt(data.getProperty("completed")));
                    String feedback = data.getProperty("feedback");
                    if (!"true".equals(feedback) && !"false".equals(feedback)) {
                        throw new IllegalArgumentException("Invalid feedback preference");
                    }
                    records.put(id, new PlayerRecord(name, count, Boolean.parseBoolean(feedback)));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid counter file; preserved for recovery: " + path.getFileName(), e);
                }
            }
        }
        return records;
    }

    public void save(UUID id, PlayerRecord record) throws IOException {
        Properties data = new Properties();
        data.setProperty("name", record.name());
        data.setProperty("total", Integer.toString(record.count().total()));
        data.setProperty("completed", Integer.toString(record.count().completed()));
        data.setProperty("feedback", Boolean.toString(record.feedback()));
        Path target = directory.resolve(id + ".properties");
        Path temporary = Files.createTempFile(directory, "counter-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                data.store(writer, "DrunkShyt player counter");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Some filesystems dont support atomic moves.
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
