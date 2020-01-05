package sample;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.io.*;
import java.net.Socket;

public class Controller {
    final int BUF_SIZE = 512;
    boolean gameStarted = false;

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
    @FXML
    private Button startGameButton;

    // event listeners
    @FXML
    protected void startGame(ActionEvent event) throws IOException { // after we press Play button, we're connecting to the server
        try {
            Socket clientSocket = new Socket(serverIpTextField.getText(), Integer.parseInt(serverPortTextField.getText()));
            connectionSuccess();

            ServerListener serverListener = new ServerListener(clientSocket);
            Thread thread = new Thread(serverListener);
            thread.start();
            setStartControlsDisabled();

        } catch (Exception e) {
            connectionFailed();
        }

    }

    private void setStartControlsDisabled() {
        serverIpTextField.setDisable(true);
        serverPortTextField.setDisable(true);
        playerNameTextField.setDisable(true);
        startGameButton.setDisable(true);
    }

    // resetting app after error or end of game
    private void reset() {
        serverIpTextField.setDisable(false);
        serverPortTextField.setDisable(false);
        playerNameTextField.setDisable(false);
        startGameButton.setDisable(false);
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
            waitForEnemy();
            waitForStart();
            gameLoop();
        }

        // waiting for enemy
        private void waitForEnemy() {
            boolean enemyChosen = false;
            String clientMessage = "Player:" + playerNameTextField.getText();
            PrintWriter writer = null;
            try {
                writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(clientMessage);
            } catch (IOException e) {
                writingError();
            }
            setWaitingMessage(true);
            while (!enemyChosen) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String serverMessage = reader.readLine();
                    if (serverMessage.contains("enemy")) {
                        sendEnemyInfo(serverMessage);
                        enemyChosen = true;
                        setWaitingMessage(false);
                    }
                } catch (Exception e) {
                    readingError();
                }
            }
        }

        // waiting for start
        private void waitForStart() {
            setWaitingMessage(true);
            while (true) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String serverMessage = reader.readLine();
                    if (serverMessage.contains("start")) {
                        gameInfo("Game started!");
                        setWaitingMessage(false);
                        return;
                    }
                } catch (Exception e) {
                    readingError();
                }
            }
        }

        // main game loop
        private void gameLoop() {

            while (true) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String serverMessage = reader.readLine();
                    handleServerMessage(serverMessage);
                } catch (Exception e) {
                    readingError();
                }
            }
        }

        // we're checking meaning of message from server
        private void handleServerMessage(String serverMessage) {
            Platform.runLater(() -> debugLabel.setText(serverMessage));

            if (serverMessage.contains("Ohit")) handleOurHit(serverMessage);
            else if (serverMessage.contains("Osink")) handleOurHit(serverMessage);
            else if (serverMessage.contains("Omiss")) handleOurMiss(serverMessage);
            else if (serverMessage.contains("hit")) handleHit(serverMessage);
            else if (serverMessage.contains("sink")) handleHit(serverMessage);
            else if (serverMessage.contains("miss")) handleMiss(serverMessage);
            else if (serverMessage.contains("won")) gameInfo("Congratulations! you've won!");
            else if (serverMessage.contains("lose")) gameInfo("You've lost!");
            else if (serverMessage.contains("error")) serverError();
            else if (serverMessage.contains("turn")) setTurn(serverMessage);
            else {
                // TODO: Handler for unexpected situations
            }
        }

        // sending information of our enemy name
        private void sendEnemyInfo(String message) {
            String enemyName = message.substring(6, message.length());
            Platform.runLater(() -> enemyLabel.setText("Enemy: " + enemyName));
            gameInfo("You're playing with: " + enemyName);
            setWaitingMessage(false);
        }

        // we're setting the message about turn
        private void setTurn(String message) {
            if(message.contains("our")) setWaitingMessage(false);
            else setWaitingMessage(true);
        }
        // we're setting the point in the grid orange when we're hit an enemy ship
        private void handleHit(String message) {
        }

        // we're setting the whole sunk enemy ship in red
        private void handleHitAndSink(String message) {
        }

        // we're setting the point in the grid grey when enemy miss our ship
        private void handleMiss(String message) {
        }

        // we're setting the point in the grid orange when enemy hit our ship
        private void handleOurHit(String message) {
        }

        // we're setting the whole sunk our ship in red
        private void handleOurHitAndSink(String message) {
        }

        // we're setting the point in the grid grey when enemy miss our ship
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
            try {
                socket.close();
                reset();
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        // popup for error in writing
        private void writingError() {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("There was an error during writing to the server. The game will be closed");
                alert.showAndWait();
            });
            try {
                socket.close();
                reset();
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
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

        // poup with game information, no need to do different handlers
        private void gameInfo(String message) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Game Info");
                alert.setHeaderText(message);
                alert.showAndWait();
                if (message.contains("won") || message.contains("lost")) reset();
            });
        }

        // set waiting message
        private void setWaitingMessage(boolean set) {
            Platform.runLater(() -> {
                if(set)debugLabel.setText("Waiting for the server response.");
                else debugLabel.setText("");
            });
        }
    }
}
