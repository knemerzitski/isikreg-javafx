package com.knemerzitski.isikreg.date;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@JsonAdapter(Date.DateAdapter.class)
public class Date {

  public static class DateAdapter extends TypeAdapter<Date> {

    @Override
    public void write(JsonWriter jsonWriter, Date date) throws IOException {
      if (date.localDate != null)
        jsonWriter.value(date.localDate.toString());
      else if (date.zonedDateTime != null)
        jsonWriter.value(date.zonedDateTime.toInstant().toString());
      else
        jsonWriter.nullValue();
    }

    @Override
    public Date read(JsonReader jsonReader) throws IOException {
      String dateStr = jsonReader.nextString();
      if (dateStr.endsWith("Z")) {
        return new Date(ZonedDateTime.ofInstant(Instant.parse(dateStr), ZoneId.systemDefault()));
      } else {
        return new Date(LocalDate.parse(dateStr));
      }
    }
  }

  public static String untilText1(LocalDateTime before, LocalDateTime after) {
    long diffH = before.until(after, ChronoUnit.HOURS);
    if (diffH > 0) {
      return diffH == 1 ? String.format("%d tund", diffH) : String.format("%d tundi", diffH);
    } else {
      long diffMin = before.until(after, ChronoUnit.MINUTES);
      if (diffMin > 0) {
        return diffMin == 1 ? String.format("%d minut", diffMin) : String.format("%d minutit", diffMin);
      } else {
        long diffSec = before.until(after, ChronoUnit.SECONDS);
        return diffSec == 1 ? String.format("%d sekund", diffSec) : String.format("%d sekundit", diffSec);
      }
    }
  }

  public static String untilText2(LocalDateTime before, LocalDateTime after) {
    long diffH = before.until(after, ChronoUnit.HOURS);
    if (diffH > 0) {
      return String.format("%d tunni", diffH);
    } else {
      long diffMin = before.until(after, ChronoUnit.MINUTES);
      if (diffMin > 0) {
        return String.format("%d minuti", diffMin);
      } else {
        long diffSec = before.until(after, ChronoUnit.SECONDS);
        return String.format("%d sekundi", diffSec);
      }
    }
  }

  private final ZonedDateTime zonedDateTime;
  private final LocalDate localDate;

  public Date(@NotNull Instant instant) {
    this.zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
    this.localDate = null;
  }

  public Date(@NotNull LocalDateTime localDateTime) {
    this.zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
    this.localDate = null;
  }


  public Date(@NotNull ZonedDateTime zonedDateTime) {
    this.zonedDateTime = zonedDateTime;
    this.localDate = null;
  }

  public Date(@NotNull LocalDate localDate) {
    this.localDate = localDate;
    this.zonedDateTime = null;
  }

  public String toIsoLocalString() {
    return zonedDateTime != null ?
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(zonedDateTime) :
        DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
  }


  public boolean hasTime() {
    return zonedDateTime != null;
  }

  public Instant toInstant() {
    if (localDate != null)
      return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
    else
      return zonedDateTime.toInstant();
  }

  public LocalDate getLocalDate() {
    return localDate != null ? localDate : zonedDateTime.toLocalDate();
  }

  public LocalDateTime getLocalDateTime() {
    return localDate != null ? localDate.atStartOfDay() : zonedDateTime.toLocalDateTime();
  }

  public ZonedDateTime getZonedDateTime() {
    return zonedDateTime != null ? zonedDateTime : localDate.atStartOfDay(ZoneId.systemDefault());
  }

  public int compareTo(Date d2) {
    return getZonedDateTime().compareTo(d2.getZonedDateTime());
  }

  public boolean isAfter(Date d2) {
    return getZonedDateTime().isAfter(d2.getZonedDateTime());
  }

  @Override
  public String toString() {
    return zonedDateTime != null ? zonedDateTime.toLocalDateTime().toString() : localDate.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Date date = (Date) o;
    return Objects.equals(zonedDateTime, date.zonedDateTime) && Objects.equals(getLocalDate(), date.getLocalDate());
  }

  @Override
  public int hashCode() {
    return Objects.hash(zonedDateTime, getLocalDate());
  }
}
