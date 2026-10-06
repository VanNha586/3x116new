package com.fishing;

import com.fishing.controllers.FishingController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FishingApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fishing_view.fxml"));
        Parent root = loader.load();

        FishingController controller = loader.getController();

        Scene scene = new Scene(root, 1000, 650);
        primaryStage.setTitle("🎣 Công Cụ Câu Cá - Fishing Tool");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (controller != null) {
                controller.saveConfig();
            }
            System.out.println("✅ Ứng dụng đóng, dữ liệu đã lưu.");
        });
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
