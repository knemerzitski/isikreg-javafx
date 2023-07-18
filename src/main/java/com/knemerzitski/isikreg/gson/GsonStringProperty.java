package com.knemerzitski.isikreg.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import javafx.beans.property.SimpleStringProperty;

import java.io.IOException;

@JsonAdapter(GsonStringProperty.GsonStringPropertyAdapter.class)
public class GsonStringProperty extends SimpleStringProperty {

  public static class GsonStringPropertyAdapter extends TypeAdapter<GsonStringProperty> {

    @Override
    public void write(JsonWriter jsonWriter, GsonStringProperty gsonStringProperty) throws IOException {
      jsonWriter.value(gsonStringProperty.get());
    }

    @Override
    public GsonStringProperty read(JsonReader jsonReader) throws IOException {
      return new GsonStringProperty(jsonReader.nextString());
    }
  }

  public GsonStringProperty() {
    super(null);
  }

  public GsonStringProperty(String initialValue) {
    super(initialValue);
  }

}
