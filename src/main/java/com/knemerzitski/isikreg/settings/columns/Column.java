package com.knemerzitski.isikreg.settings.columns;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.gsonfire.PostProcessor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.knemerzitski.isikreg.settings.columns.Column.Id.CUSTOM;

public class Column {

  public enum Type {
    TEXT(TextColumn.class),
    CHECKBOX(CheckBoxColumn.class),
    DATE(DateColumn.class),
    COMBOBOX(ComboBoxColumn.class),
    RADIO(RadioColumn.class);

    private final Class<? extends Column> columnClass;

    Type(Class<? extends Column> columnClass) {
      this.columnClass = columnClass;
    }

    public Class<? extends Column> getColumnClass() {
      return columnClass;
    }
  }

  public enum Group {
    REGISTRATION, PERSON
  }

  public enum Id {
    CUSTOM, // Custom
    REGISTERED, REGISTER_DATE, REGISTRATION_TYPE, // Per registration
    // Per person, can be read from id card
    LAST_NAME, FIRST_NAME, SEX, CITIZENSHIP,
    DATE_OF_BIRTH, PLACE_OF_BIRTH,
    PERSONAL_CODE,
    DOCUMENT_NR, EXPIRY_DATE,
    DATE_OF_ISSUANCE, PLACE_OF_ISSUANCE,
    TYPE_OF_RESIDENCE_PERMIT,
    NOTES_LINE1, NOTES_LINE2, NOTES_LINE3, NOTES_LINE4, NOTES_LINE5;

    private static final Map<String, Id> MAP = Stream.of(values())
        .collect(Collectors.toMap(Id::name, Function.identity()));

    public static Id get(String name) {
      Id id = MAP.get(name);
      return id != null ? id : CUSTOM;
    }
  }

  public static class ColumnPostProcessor implements PostProcessor<Column> {

    @Override
    public void postDeserialize(Column column, JsonElement jsonElement, Gson gson) {
      // Id check
      if (column.id == null || column.id == CUSTOM) {
        column.id = CUSTOM;
        JsonObject obj = jsonElement.getAsJsonObject();
        if (obj.has("id")) {
          column.customId = obj.get("id").getAsString();
        }
      }
    }

    @Override
    public void postSerialize(JsonElement jsonElement, Column column, Gson gson) {
      if (column.id == CUSTOM && column.customId != null) {
        jsonElement.getAsJsonObject().addProperty("id", column.customId);
      }
    }
  }

  public static class Table {
    public boolean editable = false;
  }

//  public static class Form {
//
//    public boolean required = false;
//    public boolean editable = true;
//
//    @SerializedName("default")
//    public Object initial;
//
//    public Settings.Orientation layout = Settings.Orientation.VERTICAL;
//
//    // Only for combobox
//    public String autofill = null;
//    public transient Pattern autofillPattern = null;
//    public transient ObservableList<String> autofillValues = FXCollections.observableArrayList();
//    public transient int autofillIndex = 1;
//    public transient int autoFillSelected = 0;
//    public transient String autoFillPrevious = null;
//
//    public void resetAutoFill() {
//      if (autofill == null)
//        return;
//
//      autofillValues.clear();
//      autofillIndex = 1;
//      autoFillSelected = 0;
//      autoFillPrevious = null;
//    }
//
//    public static class FormPostProcessor implements PostProcessor<Form> {
//
//      @Override
//      public void postDeserialize(Form form, JsonElement jsonElement, Gson gson) {
//        if (form.autofill != null) {
//          form.autofillPattern = StringUtils.formatToPattern(form.autofill, "%.+?d", "(\\d+)");
//        }
//      }
//
//      @Override
//      public void postSerialize(JsonElement jsonElement, Form form, Gson gson) {
//      }
//    }
//
//  }

  public static class Merge {

    public enum Rule {
      OVERWRITE_ON_EMPTY, COMBINE, NEWER, OLDER
    }

    public Rule rule = Rule.OVERWRITE_ON_EMPTY;
    public String separator = "; ";

    public static class MergeAdapter extends TypeAdapter<Merge> {

      private final TypeAdapter<Merge> delegate;

      public MergeAdapter(TypeAdapter<Merge> delegate) {
        this.delegate = delegate;
      }

      @Override
      public void write(JsonWriter jsonWriter, Merge merge) throws IOException {
        if (merge.rule == Rule.OVERWRITE_ON_EMPTY) {
          jsonWriter.value(merge.rule.toString());
        } else {
          delegate.write(jsonWriter, merge);
        }
      }

      @Override
      public Merge read(JsonReader jsonReader) throws IOException {
        if (jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
          return delegate.read(jsonReader);
        } else {
          String rule = jsonReader.nextString();
          Merge m = new Merge();
          if (Arrays.stream(Rule.values()).map(Enum::toString).anyMatch(s -> s.equals(rule))) {
            m.rule = Rule.valueOf(rule);
          } else {
            m.rule = Rule.OVERWRITE_ON_EMPTY;
          }

          return m;
        }
      }
    }

  }

  public transient boolean save = true; // Save this column in json?

  public Id id = Id.CUSTOM;
  public transient String customId;
  public Group group = Group.PERSON;

  public Type type = Type.TEXT;
  public String label;

  // Form
//  public Form form = new Form();

  // Table
  public Table table = new Table();

  public Merge merge = new Merge();

  public boolean statistics = false;

  public Column() {
  }

  public Column(Id id) {
    this.id = id;
  }

  public Column(Group group, Id id, Type type, String label, boolean tableEditable) {
    this.group = group;
    this.id = id;
    this.type = type;
    this.label = label;
    this.table.editable = tableEditable;
  }

  public Column(Group group, Id id) {
    // column without label is hidden and not saved
    this.group = group;
    this.id = id;
  }


  public String getId() {
    if (id != Id.CUSTOM) {
      return id.name();
    } else {
      if (customId != null && !customId.isEmpty()) {
        return customId;
      } else {
        return label;
      }
    }
  }

  public boolean hasForm() {
    return false;
  }

  public boolean isFormRequired() {
    return false;
  }

  public boolean hasLabel() {
    return label != null && !label.trim().isEmpty();
  }

  public String getLabel() {
    return label.replaceAll("\n", " ").trim();
  }

}

