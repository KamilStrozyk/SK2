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

    private void readingError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("There was an error during reading from the server. The game will be closed");
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
                    handleServerMessage (serverMessage);
                }catch (Exception e)
                {
                    readingError();
                    try {
                        socket.close();
                        reset();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        private void handleServerMessage(String serverMessage) {
            Platform.runLater(() -> debugLabel.setText(serverMessage));
        }
    }
}
