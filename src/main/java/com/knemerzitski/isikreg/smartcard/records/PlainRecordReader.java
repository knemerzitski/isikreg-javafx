package com.knemerzitski.isikreg.smartcard.records;

import java.util.function.Consumer;

public class PlainRecordReader extends RecordReader {

  private final Consumer<String> readRecord;

  public PlainRecordReader(Consumer<String> readRecord, Byte recordNumber) {
    super(recordNumber);
    this.readRecord = readRecord;
  }

  @Override
  protected void readRecord(String[] records, Byte[] recordNumbers) {
    readRecord.accept(records[recordNumbers[0] - 1]);
  }
}
