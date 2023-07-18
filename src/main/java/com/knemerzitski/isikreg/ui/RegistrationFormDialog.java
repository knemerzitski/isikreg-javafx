package com.knemerzitski.isikreg.ui;

import com.knemerzitski.isikreg.beans.PropertyConverter;
import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.gson.GsonBooleanProperty;
import com.knemerzitski.isikreg.gson.GsonDateProperty;
import com.knemerzitski.isikreg.settings.ColumnProperties;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.*;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RegistrationFormDialog extends Dialog<ColumnProperties> {

  private final Settings settings;

  private final ColumnProperties formProperties;
  private final Map<Column, Node> formNodes = new HashMap<>();

  public RegistrationFormDialog(Settings settings) {
    this(settings, "Uus registreerimine", "Registreeri", false);
  }

  public RegistrationFormDialog(Settings settings, String title, String saveLabel, boolean editing) {
    this.settings = settings;

    formProperties = new ColumnProperties(settings);
    setTitle(title);

    ButtonType addBtnType = new ButtonType(saveLabel, ButtonData.OK_DONE);
    ButtonType cancelBtnType = new ButtonType("Tühista", ButtonData.CANCEL_CLOSE);
    getDialogPane().getButtonTypes().addAll(addBtnType, cancelBtnType);

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20, 150, 10, 10));

    int rowIndex = 0;

    Node addButton = getDialogPane().lookupButton(addBtnType);
    addButton.setDisable(true);

    ChangeListener<Object> formValidatorListener = createFormValidationListener(addButton);

    for (Column column : settings.getFormColumns()) {
      Label label = new Label(column.label.replaceAll("\n", " ") + (column.isFormRequired() ? " *" : ""));
      grid.add(label, 0, rowIndex);
      Property<?> property = null;
      switch (column.type) {
        case TEXT:
          TextColumn textColumn = (TextColumn) column;
          TextField textField = new TextField(textColumn.form.initial != null ? textColumn.form.initial : "");
          textField.setEditable(textColumn.form.editable);
          formNodes.put(column, textField);
          textField.setPromptText(column.label);
          grid.add(textField, 1, rowIndex++);
          property = textField.textProperty();
          break;
        case DATE:
          DateColumn dateColumn = (DateColumn) column;

          DatePicker datePicker = new DatePicker();
          datePicker.setEditable(dateColumn.form.editable);
          formNodes.put(column, datePicker);
          datePicker.getEditor().setPromptText(dateColumn.label);
          datePicker.getEditor().setText(null);
          grid.add(datePicker, 1, rowIndex++);
          ObjectProperty<Date> dateProperty = new GsonDateProperty();
          PropertyConverter.bindBidirectional(datePicker.valueProperty(), dateProperty, new PropertyConverter<LocalDate, Date>() {
            @Override
            public Date convertA(LocalDate localDate) {
              return localDate != null ? new Date(localDate) : null;
            }

            @Override
            public LocalDate convertB(Date date) {
              return date != null ? date.getLocalDate() : null;
            }
          });
          property = dateProperty;

          break;
        case CHECKBOX:
          CheckBoxColumn checkBoxColumn = (CheckBoxColumn) column;
          CheckBox checkBox = new CheckBox();
          checkBox.setSelected(checkBoxColumn.form.initial);
          formNodes.put(column, checkBox);
          grid.add(checkBox, 1, rowIndex++);
          property = checkBox.selectedProperty();
          break;
        case COMBOBOX:
          ComboBoxColumn comboBoxColumn = (ComboBoxColumn) column;
          List<String> staticValues = comboBoxColumn.getOptionValues();
          if (!staticValues.isEmpty() || comboBoxColumn.form.autofill != null) {
            ComboBox<String> comboBox = new ComboBox<>();
            formNodes.put(column, comboBox);
            Node addNode = comboBox;
            comboBox.setEditable(comboBoxColumn.form.editable);
            comboBox.getItems().addAll(staticValues);
            comboBox.setPromptText("... " + (!comboBoxColumn.form.editable ? "Vali " + column.label.toLowerCase() : column.label) + " ...");

            if (comboBoxColumn.form.autofillPattern != null) {
              comboBoxColumn.form.autoFillUpdateUsePrevious = !editing;
              StringProperty autoFillStringProperty = new SimpleStringProperty();
              property = autoFillStringProperty;

              GridPane subGrid = new GridPane();
              formNodes.put(column, subGrid);
              subGrid.setAlignment(Pos.CENTER_LEFT);
              addNode = subGrid;
              subGrid.setHgap(3);
              subGrid.setVgap(3);

              CheckBox comboCheck = new CheckBox();
              comboBox.setDisable(true);
              comboCheck.selectedProperty().addListener((observable, oldValue, n) -> {
                comboBox.setDisable(!n);
              });
              subGrid.add(comboCheck, 0, 0);

              CheckBox labelCheck = new CheckBox();
              subGrid.add(labelCheck, 0, 1);
              comboBox.getItems().addAll(0, comboBoxColumn.form.autofillValues);
              subGrid.add(comboBox, 1, 0);

              Label labelAutofill = new Label(String.format(comboBoxColumn.form.autofill.getString(), comboBoxColumn.form.autofillIndex));
              subGrid.add(labelAutofill, 1, 1);

              if(editing){
                autoFillStringProperty.addListener(new ChangeListener<String>() {
                  @Override
                  public void changed(ObservableValue<? extends String> ob, String o, String n) {
                    if (comboCheck.isSelected() || labelCheck.isSelected()) {
                      autoFillStringProperty.removeListener(this);
                      return;
                    }
                    if(n != null && !n.isEmpty()){
                      comboCheck.setSelected(true);
                      comboBox.setValue(n);
                    }
                  }
                });
              }
              comboCheck.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                  labelCheck.setSelected(false);
                  if (comboBox.isEditable()) {
                    autoFillStringProperty.bindBidirectional(comboBox.getEditor().textProperty());
                  } else {
                    autoFillStringProperty.bindBidirectional(comboBox.valueProperty());
                  }
                } else if (!labelCheck.isSelected()) {
                  autoFillStringProperty.unbindBidirectional(comboBox.getEditor().textProperty());
                  autoFillStringProperty.unbindBidirectional(comboBox.valueProperty());
                  autoFillStringProperty.set(null);
                }
                if(!editing)
                  comboBoxColumn.form.autoFillSelected = newValue ? 1 : 0;
              });
              labelCheck.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                  comboCheck.setSelected(false);
                  autoFillStringProperty.unbindBidirectional(comboBox.getEditor().textProperty());
                  autoFillStringProperty.unbindBidirectional(comboBox.valueProperty());
                  autoFillStringProperty.set(labelAutofill.getText());
                } else if (!comboCheck.isSelected()) {
                  autoFillStringProperty.unbindBidirectional(comboBox.getEditor().textProperty());
                  autoFillStringProperty.unbindBidirectional(comboBox.valueProperty());
                  autoFillStringProperty.set(null);
                }
                if(!editing)
                  comboBoxColumn.form.autoFillSelected = newValue ? 2 : 0;
              });

              if (!comboBox.getItems().isEmpty()) {
                if (!editing && comboBoxColumn.form.autoFillSelected == 1)
                  comboCheck.setSelected(true);
                if (comboBoxColumn.form.autoFillPrevious == null || comboBoxColumn.form.autoFillPrevious.isEmpty()) {
                  comboBox.getSelectionModel().selectFirst();
                } else {
                  comboBox.setValue(comboBoxColumn.form.autoFillPrevious);
                }
              }
            } else if (comboBoxColumn.form.initial != null) {
              if (comboBoxColumn.form.editable) {
                comboBox.setValue(comboBoxColumn.form.initial);
              } else if (comboBox.getItems().contains(comboBoxColumn.form.initial)) {
                comboBox.setValue(comboBoxColumn.form.initial);
              }
            }

            // Simple autofill
            if (comboBoxColumn.form.isSimpleAutofill()) {
              comboBox.getItems().addAll(0, comboBoxColumn.form.autofillValues);
            }

            if (property == null) {
              if (comboBox.isEditable()) {
                property = comboBox.getEditor().textProperty();
              } else {
                StringProperty comboBoxValueStringProperty = new SimpleStringProperty();
                comboBoxValueStringProperty.bindBidirectional(comboBox.valueProperty());
                property = comboBoxValueStringProperty;
              }
            }

            if (comboBoxColumn.form.autofillPattern != null || !comboBox.getItems().isEmpty()) {
              grid.add(addNode, 1, rowIndex++);
            } else {
              grid.getChildren().remove(label);
              rowIndex--;
            }
          }
          break;
        case RADIO:
          RadioColumn radioColumn = (RadioColumn) column;
          List<String> optionLabels = radioColumn.getOptionValues();
          if (!optionLabels.isEmpty()) {
            ToggleGroup radioGroup = new ToggleGroup();

            Pane radioPane;
            if (radioColumn.form.layout == Settings.Orientation.VERTICAL) {
              radioPane = new VBox();
              ((VBox) radioPane).setSpacing(10);
            } else {
              radioPane = new HBox();
              ((HBox) radioPane).setSpacing(10);
            }

            for (String radioLabel : optionLabels) {
              RadioButton radioButton = new RadioButton(radioLabel);
              radioButton.setToggleGroup(radioGroup);
              radioPane.getChildren().add(radioButton);
            }

            formNodes.put(column, radioPane);
            grid.add(radioPane, 1, rowIndex++);

            StringProperty toggleStringProperty = new SimpleStringProperty();
            toggleStringProperty.addListener((observable, oldValue, newValue) -> {
              Optional<Toggle> optionalToggle = radioGroup.getToggles().stream().filter(toggle -> {
                if (toggle instanceof RadioButton) {
                  return ((RadioButton) toggle).getText().equals(newValue);
                }
                return false;
              }).findFirst();
              optionalToggle.ifPresent(radioGroup::selectToggle);
            });
            radioGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
              if (newValue instanceof RadioButton) {
                toggleStringProperty.setValue(((RadioButton) newValue).getText());
              }
            });
            toggleStringProperty.set(radioColumn.form.initial != null ? radioColumn.form.initial : "");
            property = toggleStringProperty;
          }
          break;
        default:
          property = new SimpleObjectProperty<>(null);
      }
      if (property != null) {
        formProperties.put(column, property);
        property.addListener(formValidatorListener);
      } else {
        grid.getChildren().remove(label);
      }
    }

    getDialogPane().setContent(grid);

    setResultConverter(btn -> {
      if (btn == addBtnType) {
        ColumnProperties properties = new ColumnProperties(settings);
        for (Map.Entry<Column, Property<?>> entry : formProperties.entrySet()) {
          Column column = entry.getKey();
          Property<?> property = entry.getValue();
          Object value = property.getValue();
          Property<?> targetProperty = settings.newProperty(column);
          if (targetProperty != null) {
            properties.put(column, targetProperty);
            if (targetProperty instanceof GsonDateProperty && value instanceof Date) {
              ((GsonDateProperty) targetProperty).setValue((Date) value);
            } else if (targetProperty instanceof StringProperty && value instanceof String) {
              ((StringProperty) targetProperty).setValue((String) value);
            } else if (targetProperty instanceof BooleanProperty && value instanceof Boolean) {
              ((BooleanProperty) targetProperty).setValue((Boolean) value);
            } else if (targetProperty instanceof GsonBooleanProperty && value instanceof Boolean) {
              ((GsonBooleanProperty) targetProperty).setValue((Boolean) value);
            }
          }
        }
        return properties;
      }
      return null;
    });
    settings.dialogHandler.centerOnShown(this);

    setOnShown(e -> {
      Node focusNode = findFocusNode(grid);
      if (focusNode != null) {
        focusNode.requestFocus();
        if (focusNode instanceof TextField) {
          ((TextField) focusNode).selectAll();
        }
      }
    });
  }

  private Node findFocusNode(Parent parent) {
    return parent.getChildrenUnmodifiable().stream()
        .filter(n -> (
            (n instanceof CheckBox) ||
                (n instanceof RadioButton) ||
                (n instanceof TextField) ||
                (n instanceof DatePicker) ||
                (n instanceof ComboBox)
        )).findFirst().orElse(null);
  }

  private ChangeListener<Object> createFormValidationListener(Node node) {
    return (l, o, n) -> {
      boolean formFilled = formProperties.entrySet().stream().allMatch(entry -> {
        if (entry.getKey().isFormRequired()) {
          Object val = entry.getValue().getValue();
          if (val == null) // if date empty then its null
            return false;
          if (val instanceof String) {
            return !((String) val).isEmpty();
          } else if (val instanceof Boolean) {
            return (Boolean) val;
          }
        }
        return true;
      });
      node.setDisable(!formFilled);
    };
  }

  public void setValues(ColumnProperties properties) {
    formProperties.setIfExists(properties);
  }

  public void setValue(Column.Id column, Property<?> newProp) {
    Property<?> existingProp = formProperties.get(settings.getColumn(column));
    if (column != null)
      ColumnProperties.setProperty(existingProp, newProp);
  }

  public void setDisable(Column.Id column, boolean disable) {
    setDisable(settings.getColumn(column), disable);
  }

  public void setDisable(Column column, boolean disable) {
    Node node = formNodes.get(column);
    if (node == null)
      return;
    node.setDisable(disable);
  }

  public Map<Column, Node> getFormNodes() {
    return formNodes;
  }

  private StringProperty stringProperty(Column.Id id) {
    Optional<? extends Property<?>> optionalProperty = formProperties.entrySet().stream()
        .filter(entry -> entry.getKey().id == id)
        .map(Map.Entry::getValue).findFirst();
    if (!optionalProperty.isPresent()) {
      return null;
    }
    Property<?> property = optionalProperty.get();
    if (property instanceof StringProperty) {
      return (StringProperty) property;
    }
    return null;
  }

  public StringProperty registrationTypeProperty() {
    return stringProperty(Column.Id.REGISTRATION_TYPE);
  }

  public StringProperty personalCodeProperty() {
    return stringProperty(Column.Id.PERSONAL_CODE);
  }

}
