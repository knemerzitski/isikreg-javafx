package com.knemerzitski.isikreg.settings.columns;

import com.google.gson.annotations.SerializedName;

public class CheckBoxColumn extends Column {

  public static class CheckBoxForm extends Form {

    @SerializedName("default")
    public boolean initial;

  }

  public CheckBoxForm form = new CheckBoxForm();

  public CheckBoxColumn() {
  }

  public CheckBoxColumn(Group group, Id id, Type type, String label, boolean form, boolean required, boolean tableEditable) {
    super(group, id, type, label, tableEditable);
    if (form) {
      this.form = new CheckBoxForm();
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
