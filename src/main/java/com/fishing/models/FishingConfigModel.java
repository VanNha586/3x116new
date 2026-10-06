package com.fishing.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Persistent store for application configuration (account details, run mode, delays, etc.).
 */
public class FishingConfigModel {
    private static final Path CONFIG_FILE = Paths.get("data", "app_config.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String username = "";
    private String password = "";
    private String runMode = "Tự động (Auto)";
    private int delaySeconds = 2;
    private boolean autoStart = false;

    public FishingConfigModel() {
        ensureDataDirectory();
        loadConfig();
    }

    public synchronized void loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(CONFIG_FILE);
            String json = new String(bytes, StandardCharsets.UTF_8);
            if (json.trim().isEmpty()) return;
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("username") && !obj.get("username").isJsonNull()) {
                    this.username = obj.get("username").getAsString();
                }
                if (obj.has("password") && !obj.get("password").isJsonNull()) {
                    this.password = obj.get("password").getAsString();
                }
                if (obj.has("runMode") && !obj.get("runMode").isJsonNull()) {
                    this.runMode = obj.get("runMode").getAsString();
                }
                if (obj.has("delaySeconds") && !obj.get("delaySeconds").isJsonNull()) {
                    this.delaySeconds = obj.get("delaySeconds").getAsInt();
                }
                if (obj.has("autoStart") && !obj.get("autoStart").isJsonNull()) {
                    this.autoStart = obj.get("autoStart").getAsBoolean();
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Không đọc được cấu hình cũ: " + e.getMessage());
        }
    }

    public synchronized void saveConfig() {
        try {
            ensureDataDirectory();
            JsonObject obj = new JsonObject();
            obj.addProperty("version", 1);
            obj.addProperty("lastUpdated", Instant.now().toString());
            obj.addProperty("username", username != null ? username : "");
            obj.addProperty("password", password != null ? password : "");
            obj.addProperty("runMode", runMode != null ? runMode : "Tự động (Auto)");
            obj.addProperty("delaySeconds", delaySeconds);
            obj.addProperty("autoStart", autoStart);

            String json = gson.toJson(obj);
            Path temp = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
            Files.write(temp, json.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, CONFIG_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, CONFIG_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("❌ Lỗi lưu cấu hình: " + e.getMessage());
        }
    }

    private void ensureDataDirectory() {
        try {
            if (CONFIG_FILE.getParent() != null) {
                Files.createDirectories(CONFIG_FILE.getParent());
            }
        } catch (IOException e) {
            System.err.println("❌ Không thể tạo thư mục data: " + e.getMessage());
        }
    }

    public synchronized String getUsername() { return username; }
    public synchronized void setUsername(String username) { this.username = username; }

    public synchronized String getPassword() { return password; }
    public synchronized void setPassword(String password) { this.password = password; }

    public synchronized String getRunMode() { return runMode; }
    public synchronized void setRunMode(String runMode) { this.runMode = runMode; }

    public synchronized int getDelaySeconds() { return delaySeconds; }
    public synchronized void setDelaySeconds(int delaySeconds) { this.delaySeconds = delaySeconds; }

    public synchronized boolean isAutoStart() { return autoStart; }
    public synchronized void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
}
