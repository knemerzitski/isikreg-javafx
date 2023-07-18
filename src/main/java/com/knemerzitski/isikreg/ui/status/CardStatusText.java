package com.knemerzitski.isikreg.ui.status;

import com.knemerzitski.isikreg.settings.ColumnProperties;
import com.knemerzitski.isikreg.smartcard.TerminalsManager.Status;
import com.knemerzitski.isikreg.utils.StringUtils;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CardStatusText {

  private final VariableStatusMessages format;

  private final List<Label> labels = new ArrayList<>();

  private String text;
  private Color color;

  public CardStatusText(VariableStatusMessages format, Label label) {
    this(format);
    add(label);
  }

  public CardStatusText(VariableStatusMessages format) {
    this.format = format;
  }

  private void set(String text, Color color) {
    this.text = text;
    this.color = color;
    labels.forEach(this::set);
  }

  private void set(ColumnProperties props, String text, Color color) {
    if (props != null) {
      Map<String, String> varMap = props.entrySet().stream()
          .collect(Collectors.toMap(
              e -> e.getKey().getId(),
              e -> e.getValue().getValue() != null ? e.getValue().getValue().toString() : ""));
      text = format.toString(varMap, text);

    }
    this.text = text;
    this.color = color;
    labels.forEach(this::set);
  }

  public List<Label> getLabels() {
    return labels;
  }

  private void set(Label label) {
    label.setText(text);
    label.setTextFill(color);
  }

  public void add(Label label) {
    set(label);
    labels.add(label);
    label.setTextAlignment(TextAlignment.CENTER);
  }

  public void add(@NotNull CardStatusText cardStatusLabel) {
    cardStatusLabel.getLabels().forEach(this::add);
  }

  public void remove(@NotNull CardStatusText cardStatusLabel) {
    labels.removeAll(cardStatusLabel.getLabels());
  }

  public void remove(Label label) {
    labels.remove(label);
  }

  public void setStatus(@NotNull Status status) {
    switch (status) {
      case PAUSED:
        paused();
        break;
      case WAITING_CARD_READER:
        waitingForCardReader();
        break;
      case WAITING_CARD:
        waitingCard();
        break;
      case READING_CARD:
        readingCard();
        break;
      case FAILED:
        failed();
        break;
      case UNRESPONSIVE_CARD:
        unresponsiveCard();
        break;
      case PROTOCOL_MISMATCH:
        protocolMismatch();
        break;
      case APDU_EXCEPTION:
        exceptionAPDU();
        break;
      case INIT:
        break;
      case SUCCESS:
        success();
        break;
      case DRIVER_MISSING:
        driverMissing();
    }
  }


  // GREEN

  public void registered(ColumnProperties props, String type) {
    set(props, StringUtils.firstCharCapitalize(type) + " registreeritud!", Color.GREEN);
  }


  // BLACK

  private void waitingForCardReader() {
    set("Sisesta ID-kaardi lugeja", Color.BLACK);
  }

  public void waitingCard() {
    set("Sisesta ID-kaart", Color.BLACK);
  }

  private void readingCard() {
    set("Loen andmeid ID-kaardilt...", Color.BLACK);
  }

  // RED

  public void notRegistered(ColumnProperties props) {
    set(props, "Ei Registreeritud!", Color.RED);
  }

  public void notOnTheList(ColumnProperties props) {
    set(props, "Pole nimekirjas!", Color.RED);
  }

  public void expired(ColumnProperties props) {
    set(props, "ID-kaart on aegunud!", Color.RED);
  }

  private void unresponsiveCard() {
    set("ID-kaarti ei tuvastatud!\nSisesta ID-kaart õigesti lugejasse.", Color.RED);
  }

  private void exceptionAPDU() {
    set("Ei suutnud ID-kaarti lugeda!\nVigane kiip?", Color.RED);
  }

  private void protocolMismatch() {
    set("Pole ID-kaart?\nVõta kaart lugejast välja.", Color.RED);
  }

  private void failed() {
    set("ID-kaardi lugemine ebaõnnestus.\nSisesta ID-kaart uuesti lugejasse.", Color.RED);
  }


  // GRAY

  private void paused() {
    set("Lugemine peatatud", Color.GRAY);
  }

  public void waitUserInput(ColumnProperties props) {
    set(props, "Ootan kasutaja sisendit!", Color.GRAY);
  }

  public void alreadyRegistered(ColumnProperties props, String type) {
    set(props, "On juba " + type.toLowerCase() + " registreeritud!", Color.GRAY);
  }

  private void success() {
    set("Andmete lugemine õnnestus.\nPalun oota!", Color.GRAY);
  }

  private void driverMissing() {
    set("Draiver puudub! Sisesta ID-kaardi\nlugeja ja taaskäivita programm.", Color.GRAY);
  }


}
