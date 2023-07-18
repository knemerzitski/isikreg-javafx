package com.knemerzitski.isikreg.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class StringAdapter extends TypeAdapter<String> {
  @Override
  public void write(JsonWriter jsonWriter, String s) throws IOException {
    jsonWriter.value(s);
  }

  @Override
  public String read(JsonReader jsonReader) throws IOException {
    if (jsonReader.peek() == JsonToken.NULL) {
      jsonReader.nextNull();
      return null;
    } else {
      return jsonReader.nextString().trim();
    }
  }
}
