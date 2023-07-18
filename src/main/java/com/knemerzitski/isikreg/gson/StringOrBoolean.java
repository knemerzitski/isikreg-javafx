package com.knemerzitski.isikreg.gson;


import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(StringOrBoolean.StringOrBooleanAdapter.class)
public class StringOrBoolean {

  public static class StringOrBooleanAdapter extends TypeAdapter<StringOrBoolean> {

    @Override
    public void write(JsonWriter jsonWriter, StringOrBoolean stringOrBoolean) throws IOException {
      if (stringOrBoolean.isString())
        jsonWriter.value(stringOrBoolean.getString());
      else
        jsonWriter.value(stringOrBoolean.getBoolean());
    }

    @Override
    public StringOrBoolean read(JsonReader jsonReader) throws IOException {
      switch (jsonReader.peek()) {
        case STRING:
          return new StringOrBoolean(jsonReader.nextString());
        case BOOLEAN:
          return new StringOrBoolean(jsonReader.nextBoolean());
        case NULL:
          jsonReader.nextNull();
      }
      return null;
    }
  }

  private final String str;
  private final boolean bool;

  public StringOrBoolean(String str) {
    this.str = str;
    this.bool = false;
  }

  public StringOrBoolean(boolean bool) {
    this.str = null;
    this.bool = bool;
  }

  public boolean isString() {
    return str != null;
  }

  public boolean isBoolean() {
    return !isString();
  }

  public String getString() {
    return str;
  }

  public boolean getBoolean() {
    return bool;
  }
}
