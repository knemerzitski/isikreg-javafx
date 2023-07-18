package com.knemerzitski.isikreg.ui;

import com.sun.glass.ui.Screen;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class StageDialogHandler extends DialogHandler {

  private final Stage stage;

  public StageDialogHandler(@NotNull Stage stage) {
    this.stage = stage;
  }

  @Override
  public boolean confirm(String header, Node content) {
    Dialog<Boolean> dialog = new Dialog<>();
    centerOnShown(dialog);
    dialog.setTitle("Kinnitus");
    ButtonType yesBtn = new ButtonType("JAH", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancelBtn = new ButtonType("EI", ButtonBar.ButtonData.CANCEL_CLOSE);
    dialog.getDialogPane().getButtonTypes().addAll(yesBtn, cancelBtn);

    VBox vBox = new VBox();
    vBox.setSpacing(10);
    dialog.getDialogPane().setContent(vBox);

    ScrollPane scrollPane = new ScrollPane();
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
    scrollPane.setMaxWidth(Math.min(dialog.getOwner().getWidth(), Screen.getMainScreen().getVisibleWidth()) - 256);
    scrollPane.setMaxHeight(Math.min(dialog.getOwner().getHeight(), Screen.getMainScreen().getVisibleHeight() - 256));

    scrollPane.setContent(content);
    vBox.getChildren().addAll(new Label(header), scrollPane);

    dialog.setResultConverter(btn -> btn == yesBtn);
    return dialog.showAndWait().orElse(false);
  }

  @Override
  public boolean confirm(String header, String content) {
    ButtonType yesBtn = new ButtonType("JAH", ButtonBar.ButtonData.OK_DONE);
    ButtonType cancelBtn = new ButtonType("EI", ButtonBar.ButtonData.CANCEL_CLOSE);
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, yesBtn, cancelBtn);

    centerOnShown(alert);

    alert.setTitle("Kinnitus");
    alert.setHeaderText(header);
    alert.setContentText(content);

    return alert.showAndWait().filter(btnType -> btnType == yesBtn).isPresent();
  }

  @Override
  public void warning(String header, String content) {
    ButtonType okBtn = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    Alert alert = new Alert(Alert.AlertType.WARNING, null, okBtn);

    centerOnShown(alert);

    alert.setTitle("Hoiatus");
    alert.setHeaderText(header);
    alert.setContentText(content);

    alert.showAndWait();
  }

  @Override
  public void exception(boolean err, String header, String content) {
    ButtonType okBtn = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    Alert alert = new Alert(err ? Alert.AlertType.ERROR : Alert.AlertType.WARNING, content, okBtn);
    centerOnShown(alert);
    alert.setTitle("Veateade");
    alert.setHeaderText(header);

    alert.showAndWait();
  }

  @Override
  public <T> void centerOnShown(Dialog<T> dialog) {
    if (stage.getScene() != null && dialog.getOwner() != stage) {
      dialog.initOwner(stage);
    }
    dialog.setOnShown(event -> {
      center(dialog);
    });
  }

  private <T> void center(Dialog<T> dialog) {
    double centerX = stage.getX() + stage.getWidth() / 2d;
    double centerY = stage.getY() + stage.getHeight() / 2d;
    dialog.setX(centerX - dialog.getWidth() / 2d);
    dialog.setY(centerY - dialog.getHeight() / 2d);
  }
}
