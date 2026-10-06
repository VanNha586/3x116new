package com.fishing.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Persistent, thread-safe log store. */
public class FishingLogModel {
    private static final Path LOG_FILE = Paths.get("data", "fishing_logs.json");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<LogEntry> logs = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final AtomicLong sequence = new AtomicLong();

    public FishingLogModel() {
        ensureDataDirectory();
        loadLogsFromFile();
    }

    public synchronized String addLog(String stream, String message) {
        long seq = sequence.incrementAndGet();
        String timestamp = LocalDateTime.now().format(FORMATTER);
        LogEntry entry = new LogEntry(seq, System.currentTimeMillis(), timestamp, stream, message);
        logs.add(entry);
        saveLogsToFile();
        return entry.format();
    }

    public synchronized List<String> getLogs() {
        List<LogEntry> ordered = new ArrayList<>(logs);
        ordered.sort(Comparator.comparingLong(LogEntry::getSequence));
        List<String> result = new ArrayList<>(ordered.size());
        for (LogEntry entry : ordered) {
            result.add(entry.format());
        }
        return result;
    }

    public synchronized int getLogCount() {
        return logs.size();
    }

    public synchronized void clearLogs() {
        logs.clear();
        sequence.set(0);
        saveLogsToFile();
    }

    private void ensureDataDirectory() {
        try {
            Files.createDirectories(LOG_FILE.getParent());
        } catch (IOException e) {
            System.err.println("❌ Không thể tạo thư mục data: " + e.getMessage());
        }
    }

    private void loadLogsFromFile() {
        if (!Files.exists(LOG_FILE)) {
            return;
        }
        try {
            String json = Files.readString(LOG_FILE, StandardCharsets.UTF_8);
            if (json.trim().isEmpty()) return;
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                JsonArray entries = object.has("entries") && object.get("entries").isJsonArray()
                        ? object.getAsJsonArray("entries") : null;
                if (entries != null) {
                    for (JsonElement element : entries) {
                        LogEntry entry = gson.fromJson(element, LogEntry.class);
                        if (entry != null) logs.add(entry);
                    }
                } else if (object.has("logs") && object.get("logs").isJsonArray()) {
                    // Backward compatibility with the previous string-only format.
                    long seq = 0;
                    for (JsonElement element : object.getAsJsonArray("logs")) {
                        String line = element.getAsString();
                        logs.add(LogEntry.legacy(++seq, line));
                    }
                }
            }
            long max = 0;
            for (LogEntry entry : logs) max = Math.max(max, entry.sequence);
            sequence.set(max);
        } catch (Exception e) {
            System.err.println("⚠️ Không đọc được nhật ký cũ: " + e.getMessage());
        }
    }

    private synchronized void saveLogsToFile() {
        try {
            ensureDataDirectory();
            State state = new State();
            state.version = 2;
            state.lastUpdated = Instant.now().toString();
            state.entries = new ArrayList<>(logs);
            String json = gson.toJson(state);

            Path temp = LOG_FILE.resolveSibling(LOG_FILE.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, LOG_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, LOG_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("❌ Lỗi lưu nhật ký: " + e.getMessage());
        }
    }

    private static final class State {
        int version;
        String lastUpdated;
        List<LogEntry> entries;
    }

    private static final class LogEntry {
        private long sequence;
        private long epochMillis;
        private String timestamp;
        private String stream;
        private String message;

        LogEntry(long sequence, long epochMillis, String timestamp, String stream, String message) {
            this.sequence = sequence;
            this.epochMillis = epochMillis;
            this.timestamp = timestamp;
            this.stream = stream;
            this.message = message;
        }

        static LogEntry legacy(long sequence, String line) {
            return new LogEntry(sequence, 0L, "", "SYSTEM", line);
        }

        String format() {
            if (timestamp == null || timestamp.isEmpty()) {
                return message == null ? "" : message;
            }
            return String.format("[%s] [%s] %s", timestamp,
                    stream == null || stream.isEmpty() ? "SYSTEM" : stream,
                    message == null ? "" : message);
        }

        long getSequence() { return sequence; }
    }
}
