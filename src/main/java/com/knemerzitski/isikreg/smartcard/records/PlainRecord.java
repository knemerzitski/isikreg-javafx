package com.knemerzitski.isikreg.smartcard.records;

public class PlainRecord extends Record<String> {

  public PlainRecord(Byte recordNumber) {
    setRecordReader(new PlainRecordReader(this, recordNumber));
  }


}
