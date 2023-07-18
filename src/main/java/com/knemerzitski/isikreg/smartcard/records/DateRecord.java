package com.knemerzitski.isikreg.smartcard.records;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateRecord extends Record<LocalDate> {

  public DateRecord(DateTimeFormatter dateFormatter, Byte recordNumber) {
    setRecordReader(new PlainRecordReader((dateString) -> {
//      try {
      accept(LocalDate.from(dateFormatter.parse(dateString)));
//        accept(dateFormatter.parse(dateString));
//      } catch (ParseException e) {
//        e.printStackTrace();
//      }
    }, recordNumber));
  }


}
