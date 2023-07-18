package com.knemerzitski.isikreg.date;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.text.Format;
import java.text.ParsePosition;
import java.time.ZoneId;
import java.time.chrono.Chronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.util.Locale;
import java.util.Set;

@JsonAdapter(DateFormatter.DateFormatterAdapter.class)
public class DateFormatter {

  public static class DateFormatterAdapter extends TypeAdapter<DateFormatter> {

    @Override
    public void write(JsonWriter jsonWriter, DateFormatter dateFormatter) throws IOException {
      jsonWriter.value(dateFormatter.pattern);
    }

    @Override
    public DateFormatter read(JsonReader jsonReader) throws IOException {
      return new DateFormatter(jsonReader.nextString());
    }
  }

  private final String pattern;
  private final DateTimeFormatter formatter;

  public DateFormatter(String pattern) {
    this.pattern = pattern;
    try {
      formatter = DateTimeFormatter.ofPattern(pattern);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(String.format("Illegal DateFormatter pattern '%s'%n%s", pattern, e.getMessage()));
    }
  }

  public String getPattern() {
    return pattern;
  }

  public DateTimeFormatter getFormatter() {
    return formatter;
  }

  // Delegated methods

  public Locale getLocale() {
    return formatter.getLocale();
  }

  public DateTimeFormatter withLocale(Locale locale) {
    return formatter.withLocale(locale);
  }

  public DecimalStyle getDecimalStyle() {
    return formatter.getDecimalStyle();
  }

  public DateTimeFormatter withDecimalStyle(DecimalStyle decimalStyle) {
    return formatter.withDecimalStyle(decimalStyle);
  }

  public Chronology getChronology() {
    return formatter.getChronology();
  }

  public DateTimeFormatter withChronology(Chronology chrono) {
    return formatter.withChronology(chrono);
  }

  public ZoneId getZone() {
    return formatter.getZone();
  }

  public DateTimeFormatter withZone(ZoneId zone) {
    return formatter.withZone(zone);
  }

  public ResolverStyle getResolverStyle() {
    return formatter.getResolverStyle();
  }

  public DateTimeFormatter withResolverStyle(ResolverStyle resolverStyle) {
    return formatter.withResolverStyle(resolverStyle);
  }

  public Set<TemporalField> getResolverFields() {
    return formatter.getResolverFields();
  }

  public DateTimeFormatter withResolverFields(TemporalField... resolverFields) {
    return formatter.withResolverFields(resolverFields);
  }

  public DateTimeFormatter withResolverFields(Set<TemporalField> resolverFields) {
    return formatter.withResolverFields(resolverFields);
  }

  public String format(TemporalAccessor temporal) {
    return formatter.format(temporal);
  }

  public void formatTo(TemporalAccessor temporal, Appendable appendable) {
    formatter.formatTo(temporal, appendable);
  }

  public TemporalAccessor parse(CharSequence text) {
    return formatter.parse(text);
  }

  public TemporalAccessor parse(CharSequence text, ParsePosition position) {
    return formatter.parse(text, position);
  }

  public <T> T parse(CharSequence text, TemporalQuery<T> query) {
    return formatter.parse(text, query);
  }

  public TemporalAccessor parseBest(CharSequence text, TemporalQuery<?>... queries) {
    return formatter.parseBest(text, queries);
  }

  public TemporalAccessor parseUnresolved(CharSequence text, ParsePosition position) {
    return formatter.parseUnresolved(text, position);
  }

  public Format toFormat() {
    return formatter.toFormat();
  }

  public Format toFormat(TemporalQuery<?> parseQuery) {
    return formatter.toFormat(parseQuery);
  }

  @Override
  public String toString() {
    return formatter.toString();
  }
}
