package com.knemerzitski.isikreg.settings.columns;

import com.google.gson.annotations.SerializedName;

public class TextColumn extends Column {

  public static class TextForm extends Form {

    public boolean editable = true;

    @SerializedName("default")
    public String initial;

  }

  public TextForm form = new TextForm();

  public TextColumn() {
  }

  public TextColumn(Group group, Id id, Type type, String label, boolean form, boolean required, boolean tableEditable) {
    super(group, id, type, label, tableEditable);
    if (form) {
      this.form = new TextForm();
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
