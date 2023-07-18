package com.knemerzitski.isikreg.smartcard.records;

public class SplitFirstNameRecord extends Record<String> {

  public SplitFirstNameRecord(Byte[] recordNumbers) {
    setRecordReader(new RecordReader(recordNumbers) {
      @Override
      protected void readRecord(String[] records, Byte[] recordNumbers) {
        String givenName1 = records[recordNumbers[0] - 1];
        String givenName2 = records[recordNumbers[1] - 1];
        accept(givenName1 + (!givenName2.isEmpty() ? '-' + givenName2 : ""));
      }
    });
  }
}
