package com.knemerzitski.isikreg.table;

import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.settings.Settings;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateTableCell<T> extends TableCell<T, Date> {

  private final DateTimeFormatter dateFormatter;
  private final DatePicker datePicker;

  public DateTableCell(Settings settings, DateTimeFormatter dateFormatter) {
    this.dateFormatter = dateFormatter;
    datePicker = new DatePicker();

    datePicker.addEventFilter(KeyEvent.KEY_PRESSED, (KeyEvent event) -> {
      if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
        try {
          datePicker.setValue(datePicker.getConverter().fromString(datePicker.getEditor().getText()));
          commitEdit(datePicker.getValue() != null ? new Date(datePicker.getValue()) : null);
        } catch (DateTimeParseException e) {
          settings.dialogHandler.warning(e.getClass().getSimpleName(), e.getMessage());
        }
      }
      if (event.getCode() == KeyCode.ESCAPE) {
        cancelEdit();
      }
    });

    datePicker.setDayCellFactory(picker -> {
      DateCell cell = new DateCell();
      cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
        datePicker.setValue(cell.getItem());
        if (event.getClickCount() == 2) {
          datePicker.hide();
          commitEdit(new Date(cell.getItem()));
        }
        event.consume();
      });
      cell.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
        if (event.getCode() == KeyCode.ENTER) {
          commitEdit(new Date(datePicker.getValue()));
        }
      });
      return cell;
    });

    contentDisplayProperty().bind(Bindings.when(editingProperty())
        .then(ContentDisplay.GRAPHIC_ONLY)
        .otherwise(ContentDisplay.TEXT_ONLY));

    setGraphic(datePicker);
  }

  @Override
  protected void updateItem(Date item, boolean empty) {
    super.updateItem(item, empty);
    if (empty || item == null) {
      setText(null);
    } else {
      setText(dateFormatter.format(item.getLocalDate()));
    }
  }

  @Override
  public void startEdit() {
    super.startEdit();
    if (!isEmpty()) {
      datePicker.setValue(getItem() != null ? getItem().getLocalDate() : null);
    }
  }

  @Override
  public void commitEdit(Date newValue) {
    super.commitEdit(newValue);
    datePicker.setValue(null);
  }

  @Override
  public void cancelEdit() {
    super.cancelEdit();
    datePicker.setValue(null);
  }

}
