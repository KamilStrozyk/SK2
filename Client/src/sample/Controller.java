package sample;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;

public class Controller {
    final int BUF_SIZE = 512;

    @FXML
    private Label debugLabel;
    @FXML
    private Label enemyLabel;
    @FXML
    private TextField serverIpTextField;
    @FXML
    private TextField serverPortTextField;
    @FXML
    private TextField playerNameTextField;

    // event listeners
    @FXML
    protected void startGame(ActionEvent event) throws IOException { // after we press Play button, we're connecting to the server
        try {
            Socket clientSocket = new Socket(serverIpTextField.getText(), Integer.parseInt(serverPortTextField.getText()));
            connectionSuccess();

            ServerListener serverListener = new ServerListener(clientSocket);
            Thread thread = new Thread(serverListener);
            thread.start();
        } catch (Exception e) {
            connectionFailed();
        }

    }

    // resetting app after error or end of game
    private void reset() {

    }

    // popups with info, their names are intuitive
    private void connectionSuccess() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Success");
        alert.setHeaderText("Connection Succeeded!");
        alert.showAndWait();
    }

    private void connectionFailed() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Connection Failed! Try again");
        alert.showAndWait();
    }

    // internal class- listener to the server
    public class ServerListener implements Runnable {
        Socket socket;

        public ServerListener(Socket clientSocket) {
            socket = clientSocket;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String serverMessage = reader.readLine();
                    handleServerMessage(serverMessage);
                } catch (Exception e) {
                    readingError();
                    try {
                        socket.close();
                        reset();
                        Thread.currentThread().interrupt();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        // we're checking meaning of message from server
        private void handleServerMessage(String serverMessage) {
            Platform.runLater(() -> debugLabel.setText(serverMessage));

            if (serverMessage.contains("enemy")) sendEnemyInfo(serverMessage);
            else if (serverMessage.contains("start")) gameInfo("The game is starting.");
            else if (serverMessage.contains("Ohit")) handleOurHit(serverMessage);
            else if (serverMessage.contains("Osink")) handleOurHit(serverMessage);
            else if (serverMessage.contains("Omiss")) handleOurMiss(serverMessage);
            else if (serverMessage.contains("hit")) handleHit(serverMessage);
            else if (serverMessage.contains("sink")) handleHit(serverMessage);
            else if (serverMessage.contains("miss")) handleMiss(serverMessage);
            else if (serverMessage.contains("won")) gameInfo("Congratulations! you've won!");
            else if (serverMessage.contains("lose")) gameInfo("You've lost!");
            else if (serverMessage.contains("error")) serverError();
            else {
                // TODO: Handler for unexpected situations
            }
        }

        // sending information of our enemy name
        private void sendEnemyInfo(String message) {
            String enemyName = message.substring(6, message.length());
            Platform.runLater(() -> enemyLabel.setText("Enemy: " + enemyName));
            gameInfo("You're playing with: " + enemyName);
        }

        //we're setting the point in the grid orange when we're hit an enemy ship
        private void handleHit(String message) {
        }

        //we're setting the whole sunk enemy ship in red
        private void handleHitAndSink(String message) {
        }

        //we're setting the point in the grid grey when enemy miss our ship
        private void handleMiss(String message) {
        }

        //we're setting the point in the grid orange when enemy hit our ship
        private void handleOurHit(String message) {
        }

        //we're setting the whole sunk our ship in red
        private void handleOurHitAndSink(String message) {
        }

        //we're setting the point in the grid grey when enemy miss our ship
        private void handleOurMiss(String message) {
        }

        // popup for error in reading
        private void readingError() {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("There was an error during reading from the server. The game will be closed");
                alert.showAndWait();
            });
        }

        // popup for server error in
        private void serverError() {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("There was unexpected server error. The game will be closed");
                alert.showAndWait();
                reset();
            });
        }

        //poup with game information, no need to do different handlers
        private void gameInfo(String message) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Game Info");
                alert.setHeaderText(message);
                alert.showAndWait();
                if (message.contains("won") || message.contains("lost")) reset();
            });
        }
    }
}
