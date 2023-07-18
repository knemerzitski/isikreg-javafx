package com.knemerzitski.isikreg.settings.adapters;

import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class BooleanObjectExpandAdapter<T> extends TypeAdapter<T> {

  private final TypeAdapter<T> delegate;

  public BooleanObjectExpandAdapter(TypeAdapter<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void write(JsonWriter jsonWriter, T t) throws IOException {
    if (t != null) {
      delegate.write(jsonWriter, t);
    } else {
      jsonWriter.value(false);
    }
  }

  @Override
  public T read(JsonReader jsonReader) throws IOException {
    if (jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
      return delegate.read(jsonReader);
    } else {
      boolean createObject = jsonReader.nextBoolean();
      if (createObject) {
        return delegate.fromJsonTree(new JsonObject());
      } else {
        return null;
      }
    }
  }
}
