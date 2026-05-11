package netpro2320603;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root  = FXMLLoader.load(getClass().getResource("chat.fxml"));
        Scene  scene = new Scene(root, 1200, 720);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        primaryStage.setTitle("TCPChat603JFX  |  netpro2320603");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }

    public static void main(String[] args) {
        Thread serverThread = new Thread(() -> {
            try {
                new ChatServer(20603).start();
            } catch (Exception e) {
                System.err.println("[Main] Server error: " + e.getMessage());
            }
        });
        serverThread.setName("ChatServer-Thread");
        serverThread.setDaemon(true);
        serverThread.start();

        try { Thread.sleep(300); } catch (InterruptedException ignored) {}

        launch(args);
    }
}