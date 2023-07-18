package com.knemerzitski.isikreg.settings.columns;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.knemerzitski.isikreg.gson.StringOrBoolean;
import com.knemerzitski.isikreg.utils.StringUtils;
import io.gsonfire.PostProcessor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.regex.Pattern;

public class ComboBoxColumn extends OptionsColumn {

  public static class ComboBoxForm extends Form {

    public static class ComboBoxFormPostProcessor implements PostProcessor<ComboBoxForm> {

      @Override
      public void postDeserialize(ComboBoxForm form, JsonElement jsonElement, Gson gson) {
        if (form.autofill != null) {
          if (form.autofill.isString()) {
            form.autofillPattern = StringUtils.formatToPattern(form.autofill.getString(), "%.+?d", "(\\d+)");
          }
        }
      }

      @Override
      public void postSerialize(JsonElement jsonElement, ComboBoxForm form, Gson gson) {
      }
    }

    public boolean editable = false;

    @SerializedName("default")
    public String initial;

    public StringOrBoolean autofill = null;

    public transient ObservableList<String> autofillValues = FXCollections.observableArrayList();
    public transient Pattern autofillPattern = null;
    public transient int autofillIndex = 1;
    public transient int autoFillSelected = 0;
    public transient String autoFillPrevious = null;
    public transient boolean autoFillUpdateUsePrevious = true;

    public void resetAutoFill() {
      if (autofill == null)
        return;

      autofillValues.clear();
      autofillIndex = 1;
      autoFillSelected = 0;
      autoFillPrevious = null;
    }

    public boolean isSimpleAutofill() {
      return autofill != null && autofill.isBoolean() && autofill.getBoolean();
    }

  }

  public ComboBoxForm form = new ComboBoxForm();

  public ComboBoxColumn() {
  }

  public ComboBoxColumn(Group group, Id id, Type type, String label, boolean form, boolean required, boolean tableEditable, List<Option> options) {
    super(group, id, type, label, tableEditable, options);
    if (form) {
      this.form = new ComboBoxForm();
      this.form.required = required;
    } else {
      this.form = null;
    }
  }

  @Override
  public boolean hasForm() {
    return form != null;
  }

  @Override
  public boolean isFormRequired() {
    return form.required;
  }

}
