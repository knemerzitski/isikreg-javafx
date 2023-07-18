package com.knemerzitski.isikreg.smartcard.records;

public abstract class RecordReader {

  protected final Byte[] recordNumbers;

  public RecordReader(Byte recordNumber) {
    this(new Byte[]{recordNumber});
  }

  public RecordReader(Byte[] recordNumbers) {
    this.recordNumbers = recordNumbers;
  }

  protected abstract void readRecord(String[] records, Byte[] recordNumbers);

  public void readRecord(String[] records) {
    readRecord(records, recordNumbers);
  }

  public Byte[] getRecordNumbers() {
    return recordNumbers;
  }

}
