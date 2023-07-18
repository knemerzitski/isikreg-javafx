package com.knemerzitski.isikreg.ui.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VariableStatusMessage {

  private interface Part {
    String get(Map<String, String> variableMapper, String event);
  }

  private static class StringPart implements Part {

    private final String str;

    public StringPart(String str) {
      this.str = str;
    }

    @Override
    public String get(Map<String, String> variableMapper, String event) {
      return str;
    }
  }

  private static class VariablePart implements Part {

    private final String key;

    public VariablePart(String key) {
      this.key = key;
    }

    @Override
    public String get(Map<String, String> variableMapper, String event) {
      String v = variableMapper.get(key);
      return v != null ? v : "";
    }
  }

  private static class EventPart implements Part {

    @Override
    public String get(Map<String, String> variableMapper, String event) {
      return event;
    }
  }

  private static final Pattern pattern = Pattern.compile("\\{(?<id>.+?)}|@(?<event>event)");
  //\$(?<id>[\w\d_-]+)|@(?<event>event)
  //\$\{(?<id>.+)}|@(?<event>event)
  //\$\{(?<id>.+?[^\\])}|@(?<event>event)

  public static VariableStatusMessage parse(String format) {
    VariableStatusMessage sb = new VariableStatusMessage(format);
    Matcher m = pattern.matcher(format);
    int startIndex = 0;
    while (m.find()) {
      if (m.group("id") != null) {
        sb.addConstant(format.substring(startIndex, m.start()));
        sb.addVariable(m.group("id"));
        startIndex = m.end();
      } else if (m.group("event") != null) {
        sb.addConstant(format.substring(startIndex, m.start()));
        sb.addEvent();
        startIndex = m.end();
      }
    }

    return sb;
  }

  private final List<Part> parts = new ArrayList<>();
  private final String format;

  public VariableStatusMessage(String format) {
    this.format = format;
  }

  public void addConstant(String str) {
    parts.add(new StringPart(str));
  }

  public void addVariable(String name) {
    parts.add(new VariablePart(name));
  }

  public void addEvent() {
    parts.add(new EventPart());
  }

  public boolean hasAllVariables(Map<String, String> variableMapper) {
    return parts.stream().allMatch(p -> {
      if (p instanceof VariablePart) {
        String val = variableMapper.get(((VariablePart) p).key);
        return val != null && !val.trim().isEmpty();
      } else {
        return true;
      }
    });
  }

  public List<String> getPossibleVariables() {
    return parts.stream().map(p -> {
      if (p instanceof VariablePart) {
        return ((VariablePart) p).key;
      } else {
        return null;
      }
    }).filter(Objects::nonNull).collect(Collectors.toList());
  }

  public String getFormat() {
    return format;
  }

  public String toString(Map<String, String> variableMapper, String event) {
    return parts.stream().map(p -> p.get(variableMapper, event)).collect(Collectors.joining());
  }

}
