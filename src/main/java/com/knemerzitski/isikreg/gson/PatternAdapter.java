package com.knemerzitski.isikreg.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.regex.Pattern;

public class PatternAdapter extends TypeAdapter<Pattern> {
  @Override
  public void write(JsonWriter jsonWriter, Pattern pattern) throws IOException {
    jsonWriter.value(pattern.pattern());
  }

  @Override
  public Pattern read(JsonReader jsonReader) throws IOException {
    String patternStr = jsonReader.nextString();
    return Pattern.compile(patternStr);
  }
}
