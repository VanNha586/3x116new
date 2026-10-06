package com.fishing.controllers;

import com.fishing.models.FishingConfigModel;
import com.fishing.models.FishingLogModel;
import com.fishing.services.FishingService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class FishingController implements Initializable, FishingService.FishingCallback {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> runModeComboBox;
    @FXML private TextField delayField;
    @FXML private CheckBox autoStartCheckBox;
    @FXML private Button saveConfigButton;

    @FXML private TextArea logTextArea;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Button clearButton;
    @FXML private Label statusLabel;
    @FXML private Label logCountLabel;
    @FXML private ScrollPane logScrollPane;

    private FishingLogModel logModel;
    private FishingConfigModel configModel;
    private FishingService fishingService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logModel = new FishingLogModel();
        configModel = new FishingConfigModel();
        fishingService = new FishingService(logModel);
        fishingService.setCallback(this);

        logTextArea.setWrapText(false);
        logTextArea.setStyle(
                "-fx-control-inner-background: #1e1e1e; " +
                "-fx-text-fill: #00ff00; " +
                "-fx-font-family: 'Courier New'; " +
                "-fx-font-size: 11;"
        );

        initConfigurationUI();
        initEventHandlers();

        stopButton.setDisable(true);
        loadPreviousLogs();
        updateStatus();

        if (configModel.isAutoStart()) {
            Platform.runLater(this::handleStart);
        }
    }

    private void initConfigurationUI() {
        if (runModeComboBox != null) {
            runModeComboBox.setItems(FXCollections.observableArrayList(
                    "Tự động (Auto)",
                    "Không giới hạn (Unlimited)",
                    "Theo thời gian (Timer)",
                    "Thủ công (Manual)"
            ));
        }

        // Load values from persisted FishingConfigModel
        if (usernameField != null) usernameField.setText(configModel.getUsername());
        if (passwordField != null) passwordField.setText(configModel.getPassword());
        if (runModeComboBox != null) runModeComboBox.setValue(configModel.getRunMode());
        if (delayField != null) delayField.setText(String.valueOf(configModel.getDelaySeconds()));
        if (autoStartCheckBox != null) autoStartCheckBox.setSelected(configModel.isAutoStart());

        // Attach real-time persistence listeners
        if (usernameField != null) {
            usernameField.textProperty().addListener((obs, oldV, newV) -> {
                configModel.setUsername(newV);
                configModel.saveConfig();
            });
        }
        if (passwordField != null) {
            passwordField.textProperty().addListener((obs, oldV, newV) -> {
                configModel.setPassword(newV);
                configModel.saveConfig();
            });
        }
        if (runModeComboBox != null) {
            runModeComboBox.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    configModel.setRunMode(newV);
                    configModel.saveConfig();
                }
            });
        }
        if (delayField != null) {
            delayField.textProperty().addListener((obs, oldV, newV) -> {
                try {
                    int val = Integer.parseInt(newV.trim());
                    configModel.setDelaySeconds(val);
                    configModel.saveConfig();
                } catch (NumberFormatException ignored) {}
            });
        }
        if (autoStartCheckBox != null) {
            autoStartCheckBox.selectedProperty().addListener((obs, oldV, newV) -> {
                configModel.setAutoStart(newV);
                configModel.saveConfig();
            });
        }
    }

    private void initEventHandlers() {
        if (startButton != null) startButton.setOnAction(e -> handleStart());
        if (stopButton != null) stopButton.setOnAction(e -> handleStop());
        if (clearButton != null) clearButton.setOnAction(e -> handleClear());
        if (saveConfigButton != null) saveConfigButton.setOnAction(e -> handleSaveConfig());
    }

    public void saveConfig() {
        if (configModel != null) {
            configModel.saveConfig();
        }
    }

    private void handleSaveConfig() {
        saveConfig();
        if (logModel != null) {
            logModel.addLog("SYSTEM", "💾 Đã lưu cấu hình tài khoản và chế độ chạy thành công.");
            loadPreviousLogs();
        }
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

    public FishingConfigModel getConfigModel() {
        return configModel;
    }
}
