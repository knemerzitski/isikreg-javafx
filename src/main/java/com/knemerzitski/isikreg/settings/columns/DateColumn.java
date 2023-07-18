package com.knemerzitski.isikreg.settings.columns;

import com.knemerzitski.isikreg.date.DateFormatter;

public class DateColumn extends Column {

  public static class DateForm extends Form {

    public boolean editable = true;

  }

  private static final DateFormatter defaultDateFormat = new DateFormatter("dd.MM.yyyy");

  public DateFormatter dateFormat = defaultDateFormat;

  public DateForm form = new DateForm();

  public DateColumn() {
  }

  public DateColumn(Group group, Id id, Type type, String label, boolean form, boolean required, boolean tableEditable, String dateFormat) {
    super(group, id, type, label, tableEditable);
    if (form) {
      this.form = new DateForm();
      this.form.required = required;
    } else {
      this.form = null;
    }
    this.dateFormat = new DateFormatter(dateFormat);
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
