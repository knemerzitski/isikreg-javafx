package com.knemerzitski.isikreg.smartcard.records;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatePlaceRecordReader extends RecordReader {

  private final DateTimeFormatter dateFormatter;
  private final Pattern pattern;

  private final Record<LocalDate> dateRecord;
  private final Record<String> placeRecord;

  public DatePlaceRecordReader(Pattern pattern, DateTimeFormatter dateFormatter, byte recordNumber) {
    super(recordNumber);
    this.pattern = pattern;
    this.dateFormatter = dateFormatter;

    this.dateRecord = new Record<>(this);
    this.placeRecord = new Record<>(this);
  }

  public Record<LocalDate> getDateRecord() {
    return dateRecord;
  }

  public Record<String> getPlaceRecord() {
    return placeRecord;
  }

  @Override
  protected void readRecord(String[] records, Byte[] recordNumbers) {
    String value = records[recordNumbers[0] - 1];
    if (value == null)
      return;
    Matcher m = pattern.matcher(value);
    if (!m.find())
      return;
//    try {
    dateRecord.accept(LocalDate.from(dateFormatter.parse(m.group(1))));
//      dateRecord.accept(dateFormat.parse(m.group(1)));
//    } catch (ParseException e) {
//      e.printStackTrace();
//    }
    placeRecord.accept(m.group(2));
  }
}
