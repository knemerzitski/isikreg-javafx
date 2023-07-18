package com.knemerzitski.isikreg.settings;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.gson.GsonBooleanProperty;
import com.knemerzitski.isikreg.gson.GsonDateProperty;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.settings.columns.ComboBoxColumn;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonAdapter(ColumnProperties.ColumnPropertiesAdapter.class)
public class ColumnProperties extends HashMap<Column, Property<?>> {

  public static class ColumnPropertiesAdapter extends TypeAdapter<ColumnProperties> {

    private final Settings settings;
    private final Gson gson;

    public ColumnPropertiesAdapter(Settings settings, Gson gson) {
      this.settings = settings;
      this.gson = gson;
    }

    @Override
    public void write(JsonWriter jsonWriter, ColumnProperties columnProperties) throws IOException {
      jsonWriter.beginObject();
      for (Map.Entry<Column, Property<?>> entry : columnProperties.entrySet()) {
        Column column = entry.getKey();
        if (!column.hasLabel() || !column.save)
          continue;
        Property<?> property = entry.getValue();
        Object value = property.getValue();
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty()))
          continue;
        jsonWriter.name(column.getId());
        gson.toJson(property, property.getClass(), jsonWriter);
      }
      jsonWriter.endObject();
    }

    @Override
    public ColumnProperties read(JsonReader jsonReader) throws IOException {
      jsonReader.beginObject();

      ColumnProperties properties = new ColumnProperties(settings);

      while (jsonReader.hasNext()) {
        String key = jsonReader.nextName();
        Column column = settings.parseColumn(key);
        if (column == null || !column.hasLabel()) {
          // skip value since column doesn't exist
          jsonReader.skipValue();
          continue;
        }
        Class<?> klass = settings.columnPropertyClass(column);
        if (klass != null) {
          Property<?> newProperty = gson.fromJson(jsonReader, klass);
          properties.put(column, newProperty);
        }
      }

      jsonReader.endObject();
      return properties;
    }
  }

  private final Settings settings;

  public ColumnProperties(Settings settings) {
    this.settings = settings;
  }

  public static void setProperty(Property<?> property, Property<?> newProperty) {
    if (property instanceof GsonDateProperty && newProperty instanceof GsonDateProperty) {
      ((GsonDateProperty) property).set(((GsonDateProperty) newProperty).get());
    } else if (property instanceof StringProperty && newProperty instanceof StringProperty) {
      ((StringProperty) property).set(((StringProperty) newProperty).get());
    } else if (property instanceof BooleanProperty && newProperty instanceof BooleanProperty) {
      ((BooleanProperty) property).set(((BooleanProperty) newProperty).get());
    } else if (property instanceof GsonBooleanProperty && newProperty instanceof GsonBooleanProperty) {
      ((GsonBooleanProperty) property).set(((GsonBooleanProperty) newProperty).get());
    } else if (property instanceof GsonBooleanProperty && newProperty instanceof BooleanProperty) {
      ((GsonBooleanProperty) property).set(((BooleanProperty) newProperty).get());
    } else if (property instanceof BooleanProperty && newProperty instanceof GsonBooleanProperty) {
      Boolean b = ((GsonBooleanProperty) newProperty).get();
      ((BooleanProperty) property).set(b != null ? b : false);
    }
  }

  public void merge(Map<Column, Property<?>> newProperties) {
    super.forEach((column, property) -> {
      Property<?> newProperty = newProperties.get(column);
      if (property instanceof GsonDateProperty && newProperty instanceof GsonDateProperty) {
        GsonDateProperty dateProperty = (GsonDateProperty) property;
        GsonDateProperty newDateProperty = (GsonDateProperty) newProperty;
        Date date = dateProperty.get();
        Date newDate = newDateProperty.get();
        switch (column.merge.rule) {
          case OVERWRITE_ON_EMPTY:
            if (date == null)
              dateProperty.set(newDate);
            break;
          case NEWER:
            if (date == null) {
              dateProperty.set(newDate);
            } else if (newDate != null) {
              if (newDate.isAfter(date)) {
                dateProperty.set(newDate);
              }
            }
            break;
          case OLDER:
            if (date == null) {
              dateProperty.set(newDate);
            } else if (newDate != null) {
              if (date.isAfter(newDate)) {
                dateProperty.set(newDate);
              }
            }
            break;
        }
      } else if (property instanceof StringProperty && newProperty instanceof StringProperty) {
        StringProperty stringProperty = (StringProperty) property;
        StringProperty newStringProperty = (StringProperty) newProperty;
        String string = stringProperty.get();
        String newString = newStringProperty.get();
        switch (column.merge.rule) {
          case OVERWRITE_ON_EMPTY:
            if (string == null || string.trim().isEmpty()) {
              stringProperty.set(newString);
            }
            break;
          case COMBINE:
            String[] values = string != null ? string.split(column.merge.separator.trim()) : new String[0];
            String[] newValues = newString != null ? newString.split(column.merge.separator.trim()) : new String[0];
            stringProperty.set(Stream.concat(Arrays.stream(values), Arrays.stream(newValues))
                .map(String::trim).distinct().filter(v -> !v.isEmpty()).collect(Collectors.joining(column.merge.separator)));
            break;
        }
      } else if (property instanceof BooleanProperty && newProperty instanceof BooleanProperty) {
        BooleanProperty booleanProperty = (BooleanProperty) property;
        BooleanProperty newBooleanProperty = (BooleanProperty) newProperty;
        booleanProperty.set(booleanProperty.get() || newBooleanProperty.get());
      } else if (property instanceof GsonBooleanProperty && newProperty instanceof GsonBooleanProperty) {
        GsonBooleanProperty booleanProperty = (GsonBooleanProperty) property;
        GsonBooleanProperty newBooleanProperty = (GsonBooleanProperty) newProperty;
        Boolean bool = newBooleanProperty.get();
        Boolean newBool = newBooleanProperty.get();
        switch (column.merge.rule) {
          case OVERWRITE_ON_EMPTY:
            if (bool == null) {
              booleanProperty.set(newBool);
            }
            break;
        }
      }
    });
  }


  public void setIfExists(Map<Column, Property<?>> newProps) {
    super.forEach((column, property) -> {
      Property<?> newProp = newProps.get(column);
      setProperty(property, newProp);
    });
  }

  public void updateFormAutoFillIndex() {
    super.forEach((column, property) -> {
      Object value = property.getValue();

      if (column.type == Column.Type.COMBOBOX) {
        ComboBoxColumn comboBoxColumn = (ComboBoxColumn) column;
        // Update COMBOBOX autofill values
        if (comboBoxColumn.hasForm() && (comboBoxColumn.form.autofillPattern != null || comboBoxColumn.form.isSimpleAutofill()) &&
            property instanceof StringProperty && value instanceof String) {
          if (comboBoxColumn.form.autoFillSelected == 2) {
            comboBoxColumn.form.autoFillSelected = 1;
          }
          String str = (String) value;
          if (str.isEmpty())
            return;

          // Update autofill index
          Pattern pattern = comboBoxColumn.form.autofillPattern;
          if (pattern != null) {
            Matcher matcher = pattern.matcher(str);
            if (matcher.find() && matcher.groupCount() != 0) {
              String digitStr = matcher.group(1);
              if (NumberUtils.isParsable(digitStr)) {
                int index = Integer.parseInt(digitStr);
                if (comboBoxColumn.form.autofillIndex <= index)
                  comboBoxColumn.form.autofillIndex = index + 1;
              }
            }
          }
          if (!comboBoxColumn.form.autofillValues.contains(str) && !comboBoxColumn.getOptionValues().contains(str)) {
            comboBoxColumn.form.autofillValues.add(0, str);
          }
          if(comboBoxColumn.form.autoFillUpdateUsePrevious){
            comboBoxColumn.form.autoFillPrevious = str;
          }
        }
      }
    });
  }

  public void setOrPutIfAbsent(Map<Column, Property<?>> newProps) {
    newProps.forEach((column, newProp) -> {
      Property<?> prop = super.get(column);
      if (prop != null) {
        setProperty(prop, newProp);
      } else {
        super.put(column, newProp);
      }
    });
  }

  public String getString(Column.Id id) {
    Property<?> property = get(settings.getColumn(id));
    if (property instanceof StringProperty) {
      StringProperty stringProperty = (StringProperty) property;
      return stringProperty.get() != null ? stringProperty.get() : "";
    }
    return "";
  }

  public String getLastName() {
    return getString(Column.Id.LAST_NAME);
  }

  public String getFirstName() {
    return getString(Column.Id.FIRST_NAME);
  }

  public String getPersonalCode() {
    return getString(Column.Id.PERSONAL_CODE);
  }

  public String getRegistrationType() {
    return getString(Column.Id.REGISTRATION_TYPE);
  }

  public String getPersonDisplayInfo() {
    return Arrays.stream(new String[]{getPersonalCode(), getLastName(), getFirstName()})
        .filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));
  }

  public boolean equals(ColumnProperties other) {
    return Stream.concat(keySet().stream(), other.keySet().stream()).distinct().allMatch(c -> {
      Property<?> p = get(c);
      Property<?> p2 = other.get(c);
      if (p == null) return p2 == null;
      Object o1 = p.getValue();
      Object o2 = p2.getValue();
      return (o1 == null && o2 == null) || (o1 != null && o1.equals(o2));
    });
  }

  public void setEmptyStringsToNull() {
    values().forEach(p -> {
      if (p.getValue() != null && "".equals(p.getValue())) {
        p.setValue(null);
      }
    });
  }

  @Override
  public String toString() {
    return entrySet().stream().map(e -> e.getKey().getId() + ": " + e.getValue().getValue()).collect(Collectors.joining("; "));
  }
}
