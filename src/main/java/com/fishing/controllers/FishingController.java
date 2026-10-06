package com.fishing.controllers;

import com.fishing.models.FishingLogModel;
import com.fishing.services.FishingService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;

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
        logModel = new FishingLogModel();
        fishingService = new FishingService(logModel);
        fishingService.setCallback(this);

        logTextArea.setWrapText(false);
        logTextArea.setStyle(
                "-fx-control-inner-background: #1e1e1e; " +
                "-fx-text-fill: #00ff00; " +
                "-fx-font-family: 'Courier New'; " +
                "-fx-font-size: 11;"
        );

        stopButton.setDisable(true);
        loadPreviousLogs();
        updateStatus();
    }

    private void loadPreviousLogs() {
        List<String> previousLogs = logModel.getLogs();
        logTextArea.clear();
        for (String log : previousLogs) {
            logTextArea.appendText(log);
            logTextArea.appendText(System.lineSeparator());
        }
        updateLogCount();
        scrollToBottom();
    }

    private void handleStart() {
        fishingService.startFishing();
        startButton.setDisable(fishingService.isRunning());
        stopButton.setDisable(!fishingService.isRunning());
        updateStatus();
    }

    private void handleStop() {
        stopButton.setDisable(true);
        statusLabel.setText("Trạng thái: Đang dừng...");
        fishingService.stopFishing();
        startButton.setDisable(false);
        stopButton.setDisable(true);
        updateStatus();
    }

    private void handleClear() {
        logModel.clearLogs();
        logTextArea.clear();
        updateLogCount();
    }

    private void updateStatus() {
        boolean running = fishingService.isRunning();
        statusLabel.setText(running ? "Trạng thái: Đang câu cá" : "Trạng thái: Đã dừng");
        startButton.setDisable(running);
        stopButton.setDisable(!running);
    }

    private void updateLogCount() {
        logCountLabel.setText("Tổng số log: " + logModel.getLogCount());
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            logTextArea.positionCaret(logTextArea.getLength());
            logTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    @Override
    public void onLogUpdate(String log) {
        Platform.runLater(() -> {
            logTextArea.appendText(log);
            logTextArea.appendText(System.lineSeparator());
            updateLogCount();
            scrollToBottom();
        });
    }

    @Override
    public void onFishingComplete() {
        Platform.runLater(this::updateStatus);
    }

    @Override
    public void onFishingStop() {
        Platform.runLater(this::updateStatus);
    }
}
