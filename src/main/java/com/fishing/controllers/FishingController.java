package com.fishing.controllers;

import com.fishing.models.FishingLogModel;
import com.fishing.services.FishingService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class FishingController implements Initializable, FishingService.FishingCallback {
    
    @FXML private TextArea logTextArea;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Button clearButton;
    @FXML private Label statusLabel;
    @FXML private Label logCountLabel;
    @FXML private ScrollPane logScrollPane;

    private FishingLogModel logModel;
    private FishingService fishingService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeComponents();
        loadPreviousLogs();
        setupEventHandlers();
    }

    /**
     * Khởi tạo các thành phần UI
     */
    private void initializeComponents() {
        logModel = new FishingLogModel();
        fishingService = new FishingService(logModel);
        fishingService.setCallback(this);

        if (logTextArea != null) {
            logTextArea.setWrapText(true);
            logTextArea.setStyle(
                "-fx-control-inner-background: #1e1e1e; " +
                "-fx-text-fill: #00ff00; " +
                "-fx-font-family: 'Courier New'; " +
                "-fx-font-size: 11;"
            );
        }

        if (stopButton != null) {
            stopButton.setDisable(true);
        }
        updateStatus();
    }

    /**
     * Load lại log cũ từ file (persistence)
     */
    private void loadPreviousLogs() {
        List<String> previousLogs = logModel.getLogs();
        Platform.runLater(() -> {
            if (logTextArea != null) {
                logTextArea.clear();
                for (String log : previousLogs) {
                    logTextArea.appendText(log + "\n");
                }
            }
            updateLogCount();
        });
    }

    /**
     * Cài đặt các event handler cho button
     */
    private void setupEventHandlers() {
        if (startButton != null) {
            startButton.setOnAction(e -> handleStart());
        }
        if (stopButton != null) {
            stopButton.setOnAction(e -> handleStop());
        }
        if (clearButton != null) {
            clearButton.setOnAction(e -> handleClear());
        }
    }

    private void handleStart() {
        if (startButton != null) startButton.setDisable(true);
        if (stopButton != null) stopButton.setDisable(false);
        fishingService.startFishing();
        updateStatus();
    }

    private void handleStop() {
        fishingService.stopFishing();
        if (startButton != null) startButton.setDisable(false);
        if (stopButton != null) stopButton.setDisable(true);
        updateStatus();
    }

    private void handleClear() {
        logModel.clearLogs();
        if (logTextArea != null) {
            logTextArea.clear();
        }
        updateLogCount();
    }

    private void updateStatus() {
        boolean running = fishingService.isRunning();
        if (statusLabel != null) {
            statusLabel.setText(running ? "Trạng thái: Đang chạy..." : "Trạng thái: Đang dừng");
        }
    }

    private void updateLogCount() {
        if (logCountLabel != null) {
            logCountLabel.setText("Tổng số log: " + logModel.getLogCount());
        }
    }

    @Override
    public void onLogUpdate(String log) {
        Platform.runLater(() -> {
            if (logTextArea != null) {
                logTextArea.appendText(log + "\n");
            }
            updateLogCount();
        });
    }

    @Override
    public void onFishingComplete() {
        Platform.runLater(() -> {
            if (startButton != null) startButton.setDisable(false);
            if (stopButton != null) stopButton.setDisable(true);
            updateStatus();
        });
    }

    @Override
    public void onFishingStop() {
        Platform.runLater(() -> {
            if (startButton != null) startButton.setDisable(false);
            if (stopButton != null) stopButton.setDisable(true);
            updateStatus();
        });
    }
}
