package com.knemerzitski.isikreg.ui.status;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.InputStream;

public class CardStatusPane extends StackPane {

  private final CardStatusText cardStatusText;
  private final BooleanProperty cardPresent;

  private final boolean enableCardPresentIndicator;

  public CardStatusPane(VariableStatusMessages format, int fontSize, boolean enableCardPresentIndicator) {
    this(new CardStatusText(format), new SimpleBooleanProperty(), fontSize, enableCardPresentIndicator);
  }

  public CardStatusPane(CardStatusText cardStatusText, BooleanProperty cardPresent, int fontSize, boolean enableCardPresentIndicator) {
    this.getStyleClass().add("card-status-pane");
    this.cardStatusText = cardStatusText;
    this.cardPresent = cardPresent;
    this.enableCardPresentIndicator = enableCardPresentIndicator;
    setAlignment(Pos.CENTER);

    Label statusLabel = new Label();
    statusLabel.setAlignment(Pos.CENTER);
    statusLabel.setMinHeight(Font.getDefault().getSize() * 14);
    statusLabel.setFont(Font.font("Times New Roman", FontWeight.BOLD, fontSize));
    statusLabel.setWrapText(true);
    getChildren().addAll(statusLabel);
    StackPane.setAlignment(statusLabel, Pos.CENTER);
    cardStatusText.add(statusLabel);

    if (enableCardPresentIndicator) {
      String path = "card-icon.png";
      InputStream asset = getClass().getResourceAsStream(path);
      if (asset != null) {
        Image cardIcon = new Image(asset);
        ImageView imageView = new ImageView(cardIcon);
        imageView.visibleProperty().bind(this.cardPresent);
        StackPane imagePane = new StackPane();
        imagePane.setMaxSize(-Double.MAX_VALUE, -Double.MAX_VALUE);
        imagePane.setPadding(new Insets(5));
        imagePane.getChildren().add(imageView);
        getChildren().add(imagePane);
        StackPane.setAlignment(imagePane, Pos.BOTTOM_RIGHT);
      }
    }
  }

  public CardStatusPane getCopy(int fontSize) {
    return new CardStatusPane(cardStatusText, cardPresent, fontSize, enableCardPresentIndicator);
  }

  public CardStatusText getCardStatusText() {
    return cardStatusText;
  }

  public BooleanProperty cardPresentProperty() {
    return cardPresent;
  }
}
