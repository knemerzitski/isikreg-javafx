package com.knemerzitski.isikreg.smartcard;

import com.knemerzitski.isikreg.smartcard.records.CardRecords;
import org.jetbrains.annotations.NotNull;

import javax.smartcardio.Card;
import java.util.Objects;

public class ProcessedReader {

  private final TerminalReader reader;
  private final Card card;
  private final CardRecords records;
  private final TerminalsManager.Status status;

  public ProcessedReader(@NotNull TerminalReader reader) {
    this.reader = reader;
    this.card = reader.getCurrentCard();
    this.records = reader.getCurrentCardRecords();
    this.status = reader.statusProperty().get(true);
  }

  public boolean cardChanged() {
    return card != reader.getCurrentCard();
  }

  public boolean recordsChanged() {
    return this.records != reader.getCurrentCardRecords();
  }

  public TerminalReader getReader() {
    return reader;
  }

  public Card getCard() {
    return card;
  }

  public CardRecords getRecords() {
    return records;
  }

  public boolean hasRecords() {
    return this.records != null;
  }

  public TerminalsManager.Status getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProcessedReader that = (ProcessedReader) o;
    return reader.equals(that.reader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(reader);
  }
}
