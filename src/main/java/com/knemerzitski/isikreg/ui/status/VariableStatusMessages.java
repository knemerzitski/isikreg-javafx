package com.knemerzitski.isikreg.ui.status;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonAdapter(VariableStatusMessages.VariableStatusMessagesAdapter.class)
public class VariableStatusMessages {

  public static class VariableStatusMessagesAdapter extends TypeAdapter<VariableStatusMessages> {

    @Override
    public void write(JsonWriter jsonWriter, VariableStatusMessages variableStatusMessages) throws IOException {
      if (variableStatusMessages.messages.isEmpty()) {
        jsonWriter.nullValue();
      } else if (variableStatusMessages.messages.size() == 1) {
        jsonWriter.value(variableStatusMessages.messages.get(0).getFormat());
      } else {
        jsonWriter.beginArray();
        for (VariableStatusMessage message : variableStatusMessages.messages) {
          jsonWriter.value(message.getFormat());
        }
        jsonWriter.endArray();
      }
    }

    @Override
    public VariableStatusMessages read(JsonReader jsonReader) throws IOException {
      if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
        jsonReader.beginArray();
        List<String> formats = new ArrayList<>();
        while (jsonReader.hasNext()) {
          formats.add(jsonReader.nextString());
        }
        jsonReader.endArray();
        return new VariableStatusMessages(formats);
      } else {
        return new VariableStatusMessages(jsonReader.nextString());
      }
    }
  }

  private final List<VariableStatusMessage> messages;

  public VariableStatusMessages() {
    messages = new ArrayList<>();
  }

  public VariableStatusMessages(String... formats) {
    this(Arrays.asList(formats));
  }

  public VariableStatusMessages(List<String> formats) {
    messages = formats.stream().map(VariableStatusMessage::parse).collect(Collectors.toList());
  }

  public List<VariableStatusMessage> getMessages() {
    return messages;
  }

  public String toString(Map<String, String> variableMapper, String event) {
    VariableStatusMessage msg = messages.stream().filter(m -> m.hasAllVariables(variableMapper)).findFirst().orElse(null);
    if (msg == null && !messages.isEmpty())
      msg = messages.get(0);
    return msg != null ? msg.toString(variableMapper, event) : "";
  }
}
