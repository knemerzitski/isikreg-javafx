package com.knemerzitski.isikreg.settings.columns;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OptionsColumn extends Column {

  public List<Option> options = new ArrayList<>();

  public OptionsColumn() {
  }

  public OptionsColumn(Group group, Id id, Type type, String label, boolean tableEditable, List<Option> options) {
    super(group, id, type, label, tableEditable);
    this.options.addAll(options);
  }

  public List<String> getOptionValues() {
    return options.stream().filter(Option::hasLabel).map(o -> o.label).collect(Collectors.toList());
  }

  public static class Option {

    public static class OptionAdapter extends TypeAdapter<Option> {

      private final TypeAdapter<Option> delegate;

      public OptionAdapter(TypeAdapter<Option> delegate) {
        this.delegate = delegate;
      }

      @Override
      public void write(JsonWriter jsonWriter, Option option) throws IOException {
        if (option.id != null) {
          delegate.write(jsonWriter, option);
        } else {
          jsonWriter.value(option.label);
        }
      }

      @Override
      public Option read(JsonReader jsonReader) throws IOException {
        if (jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
          return delegate.read(jsonReader);
        } else {
          String label = jsonReader.nextString();
          Option o = new Option();
          o.label = label;
          return o;
        }
      }

    }

    public String id;
    public String label;

    public Option() {
    }

    public Option(String id, String label) {
      this.id = id;
      this.label = label;
    }

    public Option(String label) {
      this.label = label;
    }

    public boolean hasLabel() {
      return label != null && !label.trim().isEmpty();
    }

    @Override
    public String toString() {
      return label;
    }


  }

}
