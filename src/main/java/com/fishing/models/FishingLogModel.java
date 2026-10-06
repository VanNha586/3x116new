package com.fishing.models;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FishingLogModel {
    private List<String> logs;
    private final String LOG_FILE = "data/fishing_logs.json";
    private final Gson gson;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FishingLogModel() {
        this.logs = new ArrayList<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        ensureDataDirectory();
        loadLogsFromFile();
    }

    private void ensureDataDirectory() {
        try {
            Files.createDirectories(Paths.get("data"));
        } catch (IOException e) {
            System.err.println("❌ Lỗi tạo thư mục data: " + e.getMessage());
        }
    }

    /**
     * Thêm log mới và lưu ngay vào file
     */
    public void addLog(String category, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String fullLog = String.format("[%s] [%s] %s", timestamp, category, message);
        logs.add(fullLog);
        saveLogsToFile();
    }

    /**
     * Load dữ liệu từ file JSON khi khởi động
     */
    private void loadLogsFromFile() {
        try {
            if (!Files.exists(Paths.get(LOG_FILE))) {
                System.out.println("📝 File log chưa tồn tại, tạo mới...");
                this.logs = new ArrayList<>();
                return;
            }
            byte[] encoded = Files.readAllBytes(Paths.get(LOG_FILE));
            String json = new String(encoded, StandardCharsets.UTF_8);
            
            LogData logData = gson.fromJson(json, LogData.class);
            if (logData != null && logData.logs != null) {
                this.logs = logData.logs;
                System.out.println("✅ Đã load " + logs.size() + " log từ file");
            } else {
                this.logs = new ArrayList<>();
            }
        } catch (IOException e) {
            System.out.println("📝 File log chưa tồn tại, tạo mới...");
            this.logs = new ArrayList<>();
        }
    }

    /**
     * Lưu dữ liệu vào file JSON
     */
    private void saveLogsToFile() {
        try {
            LogData logData = new LogData();
            logData.logs = this.logs;
            logData.lastUpdated = System.currentTimeMillis();
            
            String json = gson.toJson(logData);
            Files.write(Paths.get(LOG_FILE), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("❌ Lỗi lưu file log: " + e.getMessage());
        }
    }

    /**
     * Xóa tất cả log
     */
    public void clearLogs() {
        logs.clear();
        saveLogsToFile();
    }

    /**
     * Lấy tất cả logs
     */
    public List<String> getLogs() {
        return new ArrayList<>(logs);
    }

    /**
     * Lấy số lượng log
     */
    public int getLogCount() {
        return logs.size();
    }

    /**
     * Class để lưu trong JSON
     */
    static class LogData {
        public List<String> logs;
        public long lastUpdated;
    }
}
