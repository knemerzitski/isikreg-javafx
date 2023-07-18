package com.knemerzitski.isikreg.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.knemerzitski.isikreg.date.Date;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;

public class GsonDateProperty extends SimpleObjectProperty<Date> {

  public static class GsonDatePropertyAdapter extends TypeAdapter<GsonDateProperty> {

    private final Gson gson;

    public GsonDatePropertyAdapter(Gson gson) {
      this.gson = gson;
    }

    @Override
    public void write(JsonWriter jsonWriter, GsonDateProperty dateProperty) throws IOException {
      gson.toJson(dateProperty.get(), Date.class, jsonWriter);
    }

    @Override
    public GsonDateProperty read(JsonReader jsonReader) throws IOException {
      return new GsonDateProperty(gson.fromJson(jsonReader, Date.class));
    }
  }

  public GsonDateProperty() {
    super(null);
  }

  public GsonDateProperty(Date initialValue) {
    super(initialValue);
  }

}