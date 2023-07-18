package com.knemerzitski.isikreg.utils;

import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

  public static String firstCharToLowerCase(String str) {
    if (str == null || str.length() == 0)
      return "";

    if (str.length() == 1)
      return str.toLowerCase();

    return str.substring(0, 1).toLowerCase() + str.substring(1);
  }

  public static String firstCharToUpperCase(String str) {
    if (str == null || str.length() == 0)
      return "";

    if (str.length() == 1)
      return str.toUpperCase();

    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }

  public static String firstCharCapitalize(String str) {
    if (str == null || str.length() == 0)
      return "";

    if (str.length() == 1)
      return str.toUpperCase();

    return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
  }

  public static Pattern formatToPattern(String format, String match, String replace) {
    Pattern pattern = Pattern.compile(match);
    Matcher matcher = pattern.matcher(format);
    List<Pair<Integer, Integer>> startEndList = new ArrayList<>();
    while (matcher.find())
      startEndList.add(new Pair<>(matcher.start(), matcher.end()));
    StringBuilder sb = new StringBuilder(format);

    if (!startEndList.isEmpty()) {
      // Quote end
      int start = startEndList.get(startEndList.size() - 1).getValue();
      int end = sb.length();

      if (end - start > 0)
        sb.replace(start, end, Pattern.quote(sb.substring(start, end)));
      for (int i = startEndList.size() - 1; i >= 0; i--) {
        if (i + 1 != startEndList.size()) {
          // Quote right but not end
          start = startEndList.get(i).getValue();
          end = startEndList.get(i + 1).getKey();
          if (end - start > 0)
            sb.replace(start, end, Pattern.quote(sb.substring(start, end)));
        }
        sb.replace(startEndList.get(i).getKey(), startEndList.get(i).getValue(), replace);
      }

      // Quote Start
      start = 0;
      end = startEndList.get(0).getKey();
      if (end - start > 0)
        sb.replace(start, end, Pattern.quote(sb.substring(start, end)));

      return Pattern.compile(sb.toString());
    }
    return null;
  }


  private StringUtils() {
  }
}
