package com.knemerzitski.isikreg.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;

@JsonAdapter(GsonBooleanProperty.GsonStringPropertyAdapter.class)
public class GsonBooleanProperty extends SimpleObjectProperty<Boolean> {

  public static class GsonStringPropertyAdapter extends TypeAdapter<GsonBooleanProperty> {

    @Override
    public void write(JsonWriter jsonWriter, GsonBooleanProperty gsonBooleanProperty) throws IOException {
      jsonWriter.value(gsonBooleanProperty.get());
    }

    @Override
    public GsonBooleanProperty read(JsonReader jsonReader) throws IOException {
      return new GsonBooleanProperty(jsonReader.nextBoolean());
    }
  }

  private BooleanProperty booleanProperty;

  public GsonBooleanProperty() {
    super(null);
  }

  public GsonBooleanProperty(Boolean initialValue) {
    super(initialValue);
  }

  public BooleanProperty getBooleanPropertyBinding() {
    if (booleanProperty == null) {
      booleanProperty = new SimpleBooleanProperty();
      booleanProperty.bindBidirectional(this);
    }
    return booleanProperty;
  }

}
