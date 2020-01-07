package sample;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.ConcurrentModificationException;

public class Controller {
    final int BUF_SIZE = 512;
    boolean gameStarted = false;
    int[][] boardToSent = new int[10][10];
    Socket clientSocket;

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
    @FXML
    private Button sendButton;
    @FXML
    private GridPane enemyBoard;
    @FXML
    private GridPane ourBoard;

    // event listeners
    @FXML
    protected void startGame(ActionEvent event) throws IOException { // after we press Play button, we're connecting to the server
        try {
            clientSocket = new Socket(serverIpTextField.getText(), Integer.parseInt(serverPortTextField.getText()));
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
    private void end() {
        try {
            clientSocket.close();
        } catch (IOException e) {

        }
        System.exit(0);
        Platform.exit();
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
        int shipsToSet = 9;
        int[] shipSizes = {1, 1, 1, 1, 2, 2, 2, 3, 3, 4};

        public ServerListener(Socket clientSocket) {
            socket = clientSocket;
        }

        @Override
        public void run() {
            waitForEnemy();
            prepareOurBoard();
            prepareEnemyBoard();
            waitForStart();
            gameLoop();
        }

        // waiting for enemyfsfs
        private void waitForEnemy() {
            boolean enemyChosen = false;
            String clientMessage = "Player" + playerNameTextField.getText() + "\n";
            send(clientMessage);
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
                    //send("Received/n");
                } catch (Exception e) {
                    readingError();
                }
            }
        }

        // send to server
        private void send(String clientMessage) {
            try {
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(clientMessage);
            } catch (IOException e) {
                writingError();
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
                    } else if (serverMessage.contains("board")) {
                        String clientMessage = "boare" + serverMessage.substring(5);
                        send(clientMessage);
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
            //Platform.runLater(() -> debugLabel.setText(serverMessage));

            if (serverMessage.contains("Ohit")) handleOurHit(serverMessage);
            else if (serverMessage.contains("Osink")) handleOurHitAndSink(serverMessage);
            else if (serverMessage.contains("Omiss")) handleOurMiss(serverMessage);
            else if (serverMessage.contains("hit")) handleHit(serverMessage);
            else if (serverMessage.contains("sink")) handleHitAndSink(serverMessage);
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
            if (message.contains("our")) setWaitingMessage(false);
            else setWaitingMessage(true);
        }

        private void prepareOurBoard() {
            Platform.runLater(() -> {
                debugLabel.setText("Set your ships.");
                for (int i = 1; i < 11; i++) {
                    for (int j = 1; j < 11; j++) {
                        AnchorPane boardElement = new AnchorPane();
                        boardElement.setStyle("-fx-background-color: white;-fx-border-color: black;");
                        int finalI = i;
                        int finalJ = j;
                        boardElement.setOnMouseClicked(new EventHandler<MouseEvent>() {
                            @Override
                            public void handle(MouseEvent mouseEvent) {
                                boardElement.setDisable(true);
                                setShip(finalI, finalJ);
                            }

                            private void setShip(int x, int y) {
                                if (shipsToSet > 0) {
                                    if (shipSizes[shipsToSet] == 4) {
                                        if (x > 3 && boardToSent[y - 1][x - 4] == 0 && boardToSent[y - 1][x - 2] == 0 && boardToSent[y - 1][x - 3] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 2), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 3), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 4), ourBoard);
                                            boardToSent[y - 1][x - 1] = 4;
                                            boardToSent[y - 1][x - 2] = 4;
                                            boardToSent[y - 1][x - 3] = 4;
                                            boardToSent[y - 1][x - 4] = 4;
                                            shipsToSet--;
                                        } else if (x < 8 && boardToSent[y - 1][x + 2] == 0 && boardToSent[y - 1][x + 1] == 0 && boardToSent[y - 1][x] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x + 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x + 2), ourBoard);
                                            boardToSent[y - 1][x - 1] = 4;
                                            boardToSent[y - 1][x] = 4;
                                            boardToSent[y - 1][x + 1] = 4;
                                            boardToSent[y - 1][x + 2] = 4;
                                            shipsToSet--;
                                        } else if (y > 3 && boardToSent[y - 4][x - 1] == 0 && boardToSent[y - 3][x - 1] == 0 && boardToSent[y - 2][x - 1] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 2) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 3) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 4) + Integer.toString(x - 1), ourBoard);
                                            boardToSent[y - 1][x - 1] = 4;
                                            boardToSent[y - 2][x - 1] = 4;
                                            boardToSent[y - 3][x - 1] = 4;
                                            boardToSent[y - 4][x - 1] = 4;
                                            shipsToSet--;
                                        } else if (y < 8 && boardToSent[y + 2][x - 1] == 0 && boardToSent[y + 1][x - 1] == 0 && boardToSent[y][x - 1] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y + 2) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y + 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            boardToSent[y - 1][x - 1] = 4;
                                            boardToSent[y][x - 1] = 4;
                                            boardToSent[y + 1][x - 1] = 4;
                                            boardToSent[y + 2][x - 1] = 4;
                                            shipsToSet--;
                                        }
                                    } else if (shipSizes[shipsToSet] == 3) {
                                        if (x > 2 && boardToSent[y - 1][x - 2] == 0 && boardToSent[y - 1][x - 3] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 2), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 3), ourBoard);
                                            boardToSent[y - 1][x - 1] = 3;
                                            boardToSent[y - 1][x - 2] = 3;
                                            boardToSent[y - 1][x - 3] = 3;
                                            shipsToSet--;
                                        } else if (x < 9 && boardToSent[y - 1][x + 1] == 0 && boardToSent[y - 1][x] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x + 1), ourBoard);
                                            boardToSent[y - 1][x - 1] = 3;
                                            boardToSent[y - 1][x] = 3;
                                            boardToSent[y - 1][x + 1] = 3;
                                            shipsToSet--;
                                        } else if (y > 2 && boardToSent[y - 3][x - 1] == 0 && boardToSent[y - 2][x - 1] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 2) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 3) + Integer.toString(x - 1), ourBoard);
                                            boardToSent[y - 1][x - 1] = 3;
                                            boardToSent[y - 2][x - 1] = 3;
                                            boardToSent[y - 3][x - 1] = 3;
                                            shipsToSet--;
                                        } else if (y < 9 && boardToSent[y + 1][x - 1] == 0 && boardToSent[y][x - 1] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y + 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            boardToSent[y - 1][x - 1] = 3;
                                            boardToSent[y][x - 1] = 3;
                                            boardToSent[y + 1][x - 1] = 3;
                                            shipsToSet--;
                                        }
                                    } else if (shipSizes[shipsToSet] == 2) {
                                        if (x > 1 && boardToSent[y - 1][x - 2] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 2), ourBoard);
                                            boardToSent[y - 1][x - 1] = 2;
                                            boardToSent[y - 1][x - 2] = 2;
                                            shipsToSet--;
                                        } else if (x < 10 && boardToSent[y - 1][x] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x), ourBoard);
                                            boardToSent[y - 1][x - 1] = 2;
                                            boardToSent[y - 1][x] = 2;
                                            shipsToSet--;
                                        } else if (y > 1 && boardToSent[y - 2][x - 1] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 2) + Integer.toString(x - 1), ourBoard);
                                            boardToSent[y - 1][x - 1] = 2;
                                            boardToSent[y - 2][x - 1] = 2;
                                            shipsToSet--;
                                        } else if (y < 10 && boardToSent[y][x - 1] == 0 && boardToSent[y - 1][x - 1] == 0) {
                                            changeColorOfCell("green", Integer.toString(y) + Integer.toString(x - 1), ourBoard);
                                            changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                            boardToSent[y - 1][x - 1] = 2;
                                            boardToSent[y][x - 1] = 2;
                                            shipsToSet--;
                                        }
                                    } else if (shipSizes[shipsToSet] == 1 && boardToSent[y - 1][x - 1] == 0) {
                                        changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                        boardToSent[y - 1][x - 1] = 1;
                                        shipsToSet--;
                                    }

                                } else {
                                    if (shipSizes[shipsToSet] == 1 && boardToSent[y - 1][x - 1] == 0) {
                                        changeColorOfCell("green", Integer.toString(y - 1) + Integer.toString(x - 1), ourBoard);
                                        boardToSent[y - 1][x - 1] = 1;

                                        String clientMessage = "board:";
                                        for (int i = 0; i < 10; i++) {
                                            for (int j = 0; j < 10; j++) {
                                                clientMessage += Integer.toString(boardToSent[i][j]);
                                            }
                                        }
                                        clientMessage += "\n";
                                        send(clientMessage);
                                        ourBoard.setDisable(true);
                                        setWaitingMessage(true);
                                    }
                                }
                            }
                        });
                        ourBoard.add(boardElement, i, j);
                        ourBoard.setConstraints(boardElement, i, j);
                    }
                }
            });
        }

        //prepare handlers and view for enemy board
        private void prepareEnemyBoard() {
            Platform.runLater(() -> {
                for (int i = 1; i < 11; i++) {
                    for (int j = 1; j < 11; j++) {
                        AnchorPane boardElement = new AnchorPane();
                        boardElement.setStyle("-fx-background-color: white;-fx-border-color: black;");
                        int finalI = i;
                        int finalJ = j;
                        boardElement.setOnMouseClicked(new EventHandler<MouseEvent>() {
                            @Override
                            public void handle(MouseEvent mouseEvent) {
                                boardElement.setDisable(true);
                                sendHitToServer(finalI, finalJ);
                            }

                            private void sendHitToServer(int y, int x) {
                                String clientMessage = "check:" + Integer.toString(x - 1) + Integer.toString(y - 1) + "\n";
                                send(clientMessage);
                                setWaitingMessage(true);
                            }
                        });
                        enemyBoard.add(boardElement, i, j);
                        enemyBoard.setConstraints(boardElement, i, j);
                    }
                }
            });
        }

        // we're setting the point in the grid orange when we're hit an enemy ship
        private void handleHit(String message) {
            message = message.substring(3);
            changeColorOfCell("orange", message, enemyBoard);
        }

        // we're setting the whole sunk enemy ship in red
        private void handleHitAndSink(String message) {
            message = message.substring(4);
            while (message.length() > 0) {
                changeColorOfCell("red", message, enemyBoard);
                message = message.substring(2);
            }
        }

        // we're setting the point in the grid grey when enemy miss our ship
        private void handleMiss(String message) {
            message = message.substring(4);
            changeColorOfCell("grey", message, enemyBoard);
        }

        // we're setting the point in the grid orange when enemy hit our ship
        private void handleOurHit(String message) {
            message = message.substring(4);
            changeColorOfCell("orange", message, ourBoard);
        }

        // we're setting the whole sunk our ship in red
        private void handleOurHitAndSink(String message) {
            message = message.substring(5);
            while (message.length() > 0) {
                changeColorOfCell("red", message, ourBoard);
                message = message.substring(2);
            }
        }

        // we're setting the point in the grid grey when enemy miss our ship
        private void handleOurMiss(String message) {
            message = message.substring(5);
            changeColorOfCell("grey", message, ourBoard);
        }

        private void changeColorOfCell(String color, String message, GridPane board) {
            int x = 1;
            int y = 1;
            x = Integer.parseInt(message.substring(0, 1));
            y = Integer.parseInt(message.substring(1, 2));
            int finalX = x + 1;
            int finalY = y + 1;
            Platform.runLater(() -> {
                Node boardElement = getNodeByRowColumnIndex(finalX, finalY, board);
                boardElement.setStyle("-fx-background-color: " + color + ";-fx-border-color: black;");
            });
        }

        // custom function to get children from board
        public Node getNodeByRowColumnIndex(final int row, final int column, GridPane gridPane) {
            Node result = null;
            ObservableList<Node> children = gridPane.getChildren();
            for (Node node : children) {
                try {
                    if (gridPane.getRowIndex(node) == row && gridPane.getColumnIndex(node) == column) {
                        result = node;
                        break;
                    }
                } catch (NullPointerException e) { // some values from children has null on row and/or column, this is something like filter
                }
            }

            return result;
        }

        // popup for error in reading
        private void readingError() {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("There was an error during reading from the server. The game will be closed");
                alert.showAndWait();
            });
            send("end\n");
            end();
        }

        // popup for error in writing
        private void writingError() {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("There was an error during writing to the server. The game will be closed");
                alert.showAndWait();
            });
            send("end\n");
            end();
        }

        // popup for server error in
        private void serverError() {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("There was unexpected server error. The game will be closed");
                alert.showAndWait();
                send("end\n");
                end();
            });
        }

        // poup with game information, no need to do different handlers
        private void gameInfo(String message) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Game Info");
                alert.setHeaderText(message);
                alert.showAndWait();
                if (message.contains("won") || message.contains("lost")) {
                    send("end\n");
                    end();
                }
            });
        }

        // set waiting message
        private void setWaitingMessage(boolean set) {
            Platform.runLater(() -> {
                if (set) debugLabel.setText("Waiting for the server response.");
                else debugLabel.setText("");
                enemyBoard.setDisable(set);
            });
        }
    }
}
