package com.knemerzitski.isikreg.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ShortDate extends Date {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  private static final DateFormat DISPLAY_FORMAT = new SimpleDateFormat("dd.MM HH:mm");

  public ShortDate() {
    super();
  }

  public ShortDate(Date date) {
    super(date.getTime());
  }

  public ShortDate(String s) throws ParseException {
    super(DISPLAY_FORMAT.parse(s).getTime());
  }

  @Override
  public String toString() {
    return DISPLAY_FORMAT.format(this);
  }
}
