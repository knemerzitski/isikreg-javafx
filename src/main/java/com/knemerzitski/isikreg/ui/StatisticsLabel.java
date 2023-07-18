package com.knemerzitski.isikreg.ui;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StatisticsLabel {

  public static class LabelProperty {
    private Label label;
    private IntegerProperty property;

    private ChangeListener<Number> listener;

    public LabelProperty() {
      label = new Label("0");
      property = new SimpleIntegerProperty();
      listener = (observable, oldValue, newValue) -> Platform.runLater(() -> label.setText(newValue.toString()));
      property.addListener(listener);
    }

    public Label getLabel() {
      return label;
    }

    public IntegerProperty property() {
      return property;
    }

    public void removed() {
      property.removeListener(listener);
      listener = null;
      property = null;
      label = null;
    }
  }

  private final Label textLabel = new Label();

  private final Map<String, LabelProperty> countLabels = new HashMap<>();

  public StatisticsLabel(List<String> types, String text) {
    textLabel.setText(text);
    types.forEach(type -> {
      countLabels.put(type, new LabelProperty());
    });
  }

  public Label getTextLabel() {
    return textLabel;
  }

  public List<Label> getCountLabels() {
    return countLabels.values().stream().map(v -> v.label).collect(Collectors.toList());
  }

  public Map<String, LabelProperty> getMap() {
    return countLabels;
  }

  public Label getCountLabel(String type) {
    LabelProperty r = countLabels.get(type);
    return r != null ? r.label : null;
  }

  public IntegerProperty countProperty(String type) {
    LabelProperty r = countLabels.get(type);
    return r != null ? r.property : null;
  }

  public void removed() {
    countLabels.forEach((s, labelProperty) -> labelProperty.removed());
  }
}