package com.knemerzitski.isikreg.smartcard.records;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class Record<T> implements Supplier<T>, Consumer<T> {

  private RecordReader recordReader;

  private T record;

  public Record(RecordReader recordReader) {
    this.recordReader = recordReader;
  }

  public Record() {
  }

  public void setRecordReader(RecordReader recordReader) {
    this.recordReader = recordReader;
  }

  public RecordReader getRecordReader() {
    return recordReader;
  }

  @Override
  public T get() {
    return record;
  }

  @Override
  public void accept(T record) {
    this.record = record;
  }
}
