package com.knemerzitski.isikreg.smartcard;

import com.knemerzitski.isikreg.beans.ObservableChanges;
import com.knemerzitski.isikreg.extensions.TaskExecutorTestExtension;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.settings.columns.TextColumn;
import com.knemerzitski.isikreg.smartcard.records.CardRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.smartcardio.CardTerminal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.knemerzitski.isikreg.settings.columns.Column.Group.PERSON;
import static com.knemerzitski.isikreg.settings.columns.Column.Id.*;
import static com.knemerzitski.isikreg.smartcard.TerminalsManager.Status.*;
import static org.junit.jupiter.api.Assertions.*;

public class TerminalsManagerTest extends TaskExecutorTestExtension {

  private Settings settings;

  @BeforeEach
  public void setupThis() throws IOException {
    super.setupThis();
    settings = new Settings();
    Column[] columns = {
        new TextColumn(PERSON, PERSONAL_CODE, Column.Type.TEXT, "Isikukood", true, true, false),
        new TextColumn(PERSON, LAST_NAME, Column.Type.TEXT, "Perekonnanimi", true, false, false),
        new TextColumn(PERSON, FIRST_NAME, Column.Type.TEXT, "Eesnimi", true, false, false)
    };
    settings.columns.addAll(Arrays.asList(columns));

    settings.smartCard.waitBeforeReadingCard = 0;
    settings.smartCard.showSuccessStatusDuration = 50;
    settings.smartCard.cardReadingFailedRetryInterval = 50;
    settings.smartCard.cardReadingAttemptsUntilGiveUp = 2;
    settings.smartCard.waitForChangeLoopInterval = 1; // Tests won't work with simulator otherwise, 0 will make program wait for card changes forever even if there are changes
  }

  @Test
  public void testRemoveCardNearStatusUnlockTime() throws Throwable {
    String personalCode = "3641738654";
    String lastName = "Last";
    String firstName = "Tom";
    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);

    FakeTerminalSimulator terminalSimulator = new FakeTerminalSimulator(1);
    CardTerminal terminal = terminalSimulator.getTerminal();

    TestingTerminalsManager handler = new TestingTerminalsManager(
        settings,
        terminalSimulator.getTerminalFactory(),
        getTaskExecutor());

    ObservableChanges<TerminalsManager.Status> initComplete = ObservableChanges.observableOldChange(handler.statusProperty()).value(INIT).get();
    getTaskExecutor().execute(handler);

    // Wait for init to complete before accessing reader
    initComplete.await(20, TimeUnit.MILLISECONDS);
    TerminalReader reader0 = handler.getCardTerminalReader(terminalSimulator.getTerminal().getName());


    int iterations = 2;

    for (int i = 0; i < iterations; i++) {
      //Insert card, ReadersProcessor locked listener won't be called
      ObservableChanges<TerminalsManager.Status> handlerChange = ObservableChanges.observableNewChange(handler.statusProperty())
          .value(READING_CARD).optional()
          .value(PROCESSING_CARD).get();
      ObservableChanges<TerminalsManager.Status> reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty())
          .values(READING_CARD, SUCCESS).get();
      ObservableChanges<Boolean> lockChange = ObservableChanges.observableNewChange(reader0.statusProperty().lockProperty())
          .values(true, false).get();

      card.insert(terminal);
      handlerChange.await(20, TimeUnit.MILLISECONDS);
      reader0Change.await(20, TimeUnit.MILLISECONDS);

      handler.waitForResult(20, TimeUnit.MILLISECONDS);
      assertTrue(handler.statusProperty().isLockingBound());
      assertFalse(reader0.statusProperty().isLockingBound());
      assertTrue(handler.statusProperty().isLocked());
      assertTrue(reader0.statusProperty().isLocked());

      lockChange.await(100, TimeUnit.MILLISECONDS); // Wait until no longer locked
      assertFalse(handler.statusProperty().isLockingBound());
      assertFalse(handler.statusProperty().isLocked());
      assertFalse(reader0.statusProperty().isLocked());

      handlerChange = ObservableChanges.observableNewChange(handler.statusProperty()).value(WAITING_CARD).get();
      reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty()).value(WAITING_CARD).get();
      card.eject(); // Remove card
      reader0Change.await(20, TimeUnit.MILLISECONDS);
      handlerChange.await(20, TimeUnit.MILLISECONDS);


      //Insert card but now remove it right away after processing
      handlerChange = ObservableChanges.observableNewChange(handler.statusProperty())
          .value(READING_CARD).optional()
          .value(PROCESSING_CARD).get();
      reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty())
          .values(READING_CARD, SUCCESS).get();
      lockChange = ObservableChanges.observableNewChange(reader0.statusProperty().lockProperty())
          .values(true, false).get();


      card.insert(terminal);
      handlerChange.await(20, TimeUnit.SECONDS);
      reader0Change.await(20, TimeUnit.SECONDS);
      handler.waitForResult(20, TimeUnit.SECONDS);

      handlerChange = ObservableChanges.observableNewChange(handler.statusProperty()).value(WAITING_CARD).get();
      reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty()).value(WAITING_CARD).get();
      card.eject(); // Remove card
      reader0Change.await(20, TimeUnit.SECONDS);
      handlerChange.await(20, TimeUnit.SECONDS);

      lockChange.await(100, TimeUnit.MILLISECONDS);

      assertTrue(handler.statusProperty().isLockingBound());
      assertFalse(reader0.statusProperty().isLockingBound());
      assertFalse(handler.statusProperty().isLocked());
      assertFalse(reader0.statusProperty().isLocked());
    }
  }

  @Test
  public void testReadTwoCardsOneAfterOther() throws Throwable {
    FakeTerminalSimulator.FakeCard goodCard = FakeCards.createEstIdCardBasic("3641738654", "Last", "Tom");
    FakeTerminalSimulator.FakeCard badAtrCard = FakeCards.createCardWithBadATR("3641738654_BAD_ATR", "Last_BAD_ATR", "Tom_BAD_ATR");

    FakeTerminalSimulator terminalSimulator = new FakeTerminalSimulator(2);

    TestingTerminalsManager handler = new TestingTerminalsManager(
        settings,
        terminalSimulator.getTerminalFactory(),
        getTaskExecutor());

    ObservableChanges<TerminalsManager.Status> initComplete = ObservableChanges.observableOldChange(handler.statusProperty()).value(INIT).get();
    getTaskExecutor().execute(handler);

    // Wait for init to complete before accessing reader
    initComplete.await(1, TimeUnit.SECONDS);
    TerminalReader reader0 = handler.getCardTerminalReader(terminalSimulator.getTerminal(0).getName());
    TerminalReader reader1 = handler.getCardTerminalReader(terminalSimulator.getTerminal(1).getName());

    // Insert card 0 to terminal 0
    ObservableChanges<TerminalsManager.Status> handlerChange = ObservableChanges.observableNewChange(handler.statusProperty())
        .value(READING_CARD).optional()
        .value(PROCESSING_CARD).get();
    ObservableChanges<TerminalsManager.Status> reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty())
        .values(READING_CARD, SUCCESS).get();
    goodCard.insert(terminalSimulator.getTerminal(0));
    handlerChange.await(1, TimeUnit.SECONDS);
    reader0Change.await(1, TimeUnit.SECONDS);

    // Insert badAtrCard to terminal 1
    ObservableChanges<TerminalsManager.Status> reader1Change = ObservableChanges.observableNewChange(reader1.statusProperty())
      .values(READING_CARD, SUCCESS).get();
    badAtrCard.insert(terminalSimulator.getTerminal(1));

    // Eject card 0 from terminal 0, then handler will process next card
    handlerChange = ObservableChanges.observableNewChange(handler.statusProperty())
        .value(READING_CARD).optional()
        .value(PROCESSING_CARD).get();
    reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty())
        .value(WAITING_CARD).get();
    goodCard.eject();
    reader0Change.await(1, TimeUnit.SECONDS);
    reader1Change.await(1, TimeUnit.SECONDS);
    handlerChange.await(1, TimeUnit.SECONDS);

    // Eject cart 1 from terminal 1
    handlerChange = ObservableChanges.observableNewChange(handler.statusProperty())
        .value(WAITING_CARD).get();
    reader1Change = ObservableChanges.observableNewChange(reader0.statusProperty())
        .value(WAITING_CARD).get();
    badAtrCard.eject();
    reader1Change.await(1, TimeUnit.SECONDS);
    handlerChange.await(1, TimeUnit.SECONDS);
  }

  @Test
  public void testReadUnknownCard() throws Throwable {
    FakeTerminalSimulator.FakeCard unknownCard = FakeCards.createUnknownCard();

    FakeTerminalSimulator terminalSimulator = new FakeTerminalSimulator(1);

    TestingTerminalsManager handler = new TestingTerminalsManager(
        settings,
        terminalSimulator.getTerminalFactory(),
        getTaskExecutor());

    ObservableChanges<TerminalsManager.Status> initComplete = ObservableChanges.observableOldChange(handler.statusProperty()).value(INIT).get();
    getTaskExecutor().execute(handler);

    // Wait for init to complete before accessing reader
    initComplete.await(1, TimeUnit.SECONDS);
    TerminalReader reader0 = handler.getCardTerminalReader(terminalSimulator.getTerminal().getName());

    // Insert card 0 to terminal 0
    ObservableChanges<TerminalsManager.Status> handlerChange = ObservableChanges.observableNewChange(handler.statusProperty())
        .value(READING_CARD).optional()
        .values(PROCESSING_CARD, APDU_EXCEPTION).get();
    ObservableChanges<TerminalsManager.Status> reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty())
        .values(READING_CARD, APDU_EXCEPTION).get();
    unknownCard.insert(terminalSimulator.getTerminal());
    handlerChange.await(1, TimeUnit.SECONDS);
    reader0Change.await(1, TimeUnit.SECONDS);

    ProcessedReader processedReader = handler.waitForResult(1, TimeUnit.SECONDS);
    assertFalse(processedReader.hasRecords());

    handlerChange = ObservableChanges.observableNewChange(handler.statusProperty()).value(WAITING_CARD).get();
    reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty()).value(WAITING_CARD).get();
    unknownCard.eject();
    reader0Change.await(1, TimeUnit.SECONDS);
    handlerChange.await(1, TimeUnit.SECONDS);
  }

  @Test
  public void testReadMultipleCards() throws Throwable {
    int count = 5;
    List<FakeTerminalSimulator.FakeCard> goodCards = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String personalCode = "3641738654" + i;
      String lastName = "Last" + i;
      String firstName = "Tom" + i;
      FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
      goodCards.add(card);
    }

    FakeTerminalSimulator terminalSimulator = new FakeTerminalSimulator(count);

    TestingTerminalsManager handler = new TestingTerminalsManager(
        settings,
        terminalSimulator.getTerminalFactory(),
        getTaskExecutor());

    ObservableChanges<TerminalsManager.Status> initComplete = ObservableChanges.observableOldChange(handler.statusProperty()).value(INIT).get();
    getTaskExecutor().execute(handler);

    // Wait for init to complete before continuing
    initComplete.await(1, TimeUnit.SECONDS);

    // Insert all five cards to five terminals
    ObservableChanges<TerminalsManager.Status> handlerChange = ObservableChanges.observableNewChange(handler.statusProperty())
        .value(READING_CARD).optional()
        .value(PROCESSING_CARD).get();
    for (int i = 0; i < count; i++) {
      goodCards.get(i).insert(terminalSimulator.getTerminal(i));
    }
    handlerChange.await(1, TimeUnit.SECONDS);

    for (int i = 0; i < count; i++) {
      ProcessedReader processedReader = handler.waitForResult(1, TimeUnit.SECONDS);
      assertEquals(SUCCESS, processedReader.getStatus());
      assertEquals(SUCCESS, processedReader.getReader().statusProperty().get(true));
      CardTerminal terminal = processedReader.getReader().getCardTerminal();

      ObservableChanges<TerminalsManager.Status> readerChange = ObservableChanges.observableNewChange(processedReader.getReader().statusProperty())
          .value(WAITING_CARD).get();
      if(i == count - 1){
        handlerChange = ObservableChanges.observableNewChange(handler.statusProperty())
            .value(WAITING_CARD).get();
      }
      goodCards.get(terminalSimulator.getTerminalIndex(terminal)).eject();
      readerChange.await(1, TimeUnit.SECONDS);
    }

    handlerChange.await(1, TimeUnit.SECONDS);
  }

  @Test
  public void testSuccessReadEstIdCard() throws Throwable {
    String personalCode = "36417386543";
    String lastName = "Last";
    String firstName = "Tom";
    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    assertSuccessReadEstIdCard(card, personalCode, lastName, firstName);
  }

  @Test
  public void testSuccessReadEstIdCardV2011() throws Throwable {
    String personalCode = "36417386544";
    String lastName = "Last2-La";
    String firstName = "Tom1";

    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    assertSuccessReadEstIdCard(card, personalCode, lastName, firstName);
  }

  private void assertSuccessReadEstIdCard(FakeTerminalSimulator.FakeCard card, String personalCode, String lastName, String firstName) throws Throwable {
    FakeTerminalSimulator terminalSimulator = new FakeTerminalSimulator(1);

    TestingTerminalsManager handler = new TestingTerminalsManager(
        settings,
        terminalSimulator.getTerminalFactory(),
        getTaskExecutor());

    ObservableChanges<TerminalsManager.Status> initComplete = ObservableChanges.observableOldChange(handler.statusProperty()).value(INIT).get();
    getTaskExecutor().execute(handler);

    // Wait for init to complete before accessing reader
    initComplete.await(1, TimeUnit.SECONDS);
    TerminalReader reader0 = handler.getCardTerminalReader(terminalSimulator.getTerminal().getName());

    CardTerminal terminal = terminalSimulator.getTerminal();

    ObservableChanges<TerminalsManager.Status> handlerChange = ObservableChanges.observableNewChange(handler.statusProperty())
        .value(READING_CARD).optional()
        .value(PROCESSING_CARD).get();
    ObservableChanges<TerminalsManager.Status> reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty())
        .values(READING_CARD, SUCCESS).get();

    card.insert(terminal);
    handlerChange.await(1, TimeUnit.SECONDS);
    reader0Change.await(1, TimeUnit.SECONDS);

    ProcessedReader processedReader = handler.waitForResult(1, TimeUnit.SECONDS);
    assertTrue(processedReader.hasRecords());
    CardRecords records = processedReader.getRecords();
    assertEquals(personalCode, records.getStringRecord(PERSONAL_CODE));
    assertEquals(lastName, records.getStringRecord(LAST_NAME));
    assertEquals(firstName, records.getStringRecord(FIRST_NAME));
    assertEquals(TerminalsManager.Status.LOCKED, processedReader.getReader().getStatus());
    assertEquals(TerminalsManager.Status.SUCCESS, processedReader.getReader().statusProperty().get(true));

    handlerChange = ObservableChanges.observableNewChange(handler.statusProperty()).value(WAITING_CARD).get();
    reader0Change = ObservableChanges.observableNewChange(reader0.statusProperty()).value(WAITING_CARD).get();
    card.eject(); // Done eject card now
    reader0Change.await(1, TimeUnit.SECONDS);
    handlerChange.await(1, TimeUnit.SECONDS);
  }

}
