package com.knemerzitski.isikreg.settings.columns;

import com.google.gson.annotations.SerializedName;
import com.knemerzitski.isikreg.settings.Settings;

import java.util.List;

public class RadioColumn extends OptionsColumn {


  public static class RadioForm extends Form {

    @SerializedName("default")
    public String initial;

    public Settings.Orientation layout = Settings.Orientation.VERTICAL;

  }

  public RadioForm form = new RadioForm();

  public RadioColumn() {
  }

  public RadioColumn(Group group, Id id, Type type, String label, boolean form, boolean required, boolean tableEditable, List<Option> options, Settings.Orientation layout) {
    super(group, id, type, label, tableEditable, options);
    if (form) {
      this.form = new RadioForm();
      this.form.required = required;
      this.form.layout = layout;
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
