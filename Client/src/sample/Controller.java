package sample;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class Controller {
    @FXML
    private Label debugLabel;

    @FXML protected void startGame(ActionEvent event) {
        debugLabel.setText("Sign in button pressed");
    }
}
