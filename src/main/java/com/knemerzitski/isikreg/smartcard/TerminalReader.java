package com.knemerzitski.isikreg.smartcard;

import com.knemerzitski.isikreg.beans.LockableValue;
import com.knemerzitski.isikreg.exception.AppQuitException;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.smartcard.TerminalsManager.Status;
import com.knemerzitski.isikreg.smartcard.records.CardRecords;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jetbrains.annotations.NotNull;

import javax.smartcardio.Card;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static com.knemerzitski.isikreg.smartcard.TerminalsManager.Status.*;

public abstract class TerminalReader implements Runnable {

  private interface Action {
    void run() throws CardException, UnsupportedEncodingException, APDUException;
  }

  private final TerminalsManager handler;
  private final Settings settings;

  // Input
  private CardTerminal cardTerminal;
  private final String cardTerminalName;

  // Terminal update
  private final ReentrantLock cardTerminalLock = new ReentrantLock();
  private final Condition cardTerminalPresent = cardTerminalLock.newCondition();

  // State
  private Card card;
  private final ReentrantLock cardLock = new ReentrantLock();
  private int readingAttemptsUntilGiveUp;
  private final LockableValue<Status> status;
  private final BooleanProperty cardPresent = new SimpleBooleanProperty();

  // Output
  private CardRecords cardRecords;

  // Pause
  private boolean isPaused;
  private final ReentrantLock pauseLock = new ReentrantLock();
  private final Condition unpaused = pauseLock.newCondition();
  private Status statusBeforePause;

  public TerminalReader(@NotNull TerminalsManager handler, @NotNull CardTerminal cardTerminal) {
    this.settings = handler.settings;

    this.handler = handler;
    this.cardTerminal = cardTerminal;
    this.cardTerminalName = cardTerminal.getName();

    status = new LockableValue<>(handler.taskExecutor, Status.INIT, Status.LOCKED);
  }

  @Override
  public void run() {
    Thread thread = Thread.currentThread();
    try {
      while (!thread.isInterrupted()) {
        process();
      }
    } catch (InterruptedException e) {
      if (!handler.taskExecutor.isStopping())
        throw new AppQuitException(e); // Unexpected interrupt
    }
  }

  private void process() throws InterruptedException {
    pausedLoop();
    try {

      setStatus(WAITING_CARD);

      while (!cardTerminal.waitForCardPresent(settings.smartCard.waitForChangeLoopInterval)) {
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException("Waiting for card present interrupted");
        }
        if (cardTerminal.isCardPresent()) break;
      }

      // Card present
      pausedLoop();
      cardPresent.set(true);

      unlockStatus();
      setStatus(READING_CARD);

      // Wait before reading
      waitBeforeReadingCard();
      pausedLoop();
      printInfo("Reading card records");
      read();

      if (cardTerminal.isCardPresent()) {
        printInfo("Waiting for card absent");
        while (!cardTerminal.waitForCardAbsent(settings.smartCard.waitForChangeLoopInterval)) {
          if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Waiting for card absent interrupted");
          }
          if (!cardTerminal.isCardPresent()) break;
        }
        clearCard();
        printInfo("Card absent");
        cardPresent.set(false);
      }
    } catch (CardException e) {
      cardPresent.set(false);
      if (e.getCause() != null && e.getCause().getClass().getSimpleName().equals("PCSCException")) {
        switch (TerminalsManager.CardErrorType.get(e.getCause().getMessage())) {
          case NO_SERVICE:
          case NO_READERS_AVAILABLE:
          case SERVICE_STOPPED:
            waitForNewCardTerminal();
            break;
          default:
            throw new AppQuitException(e);
        }
      } else {
        throw new AppQuitException(e);
      }
    }
  }

  private void waitBeforeReadingCard() throws InterruptedException {
    Thread.sleep(settings.smartCard.waitBeforeReadingCard);
  }

  private void waitForNewCardTerminal() throws InterruptedException {
    cardTerminalLock.lock();
    try {
      cardTerminal = null;
      setStatus(WAITING_CARD_READER);
      while (cardTerminal == null) {
        cardTerminalPresent.await();
      }
    } finally {
      cardTerminalLock.unlock();
    }
  }

  public void setCardTerminal(@NotNull CardTerminal cardTerminal) {
    cardTerminalLock.lock();
    try {
      if (!cardTerminal.getName().equals(cardTerminalName))
        throw new AppQuitException(String.format("Tried to set different terminal to the reader. Expected '%s' but was '%s'",
            cardTerminalName, cardTerminal.getName()));
      this.cardTerminal = cardTerminal;
      cardTerminalPresent.signalAll();
    } finally {
      cardTerminalLock.unlock();
    }
  }

  public String getCardTerminalName() {
    return cardTerminalName;
  }

  public void pauseRequest() {
    pauseLock.lock();
    try {
      isPaused = true;
    } finally {
      pauseLock.unlock();
    }
  }

  public void resumeRequest() {
    pauseLock.lock();
    try {
      isPaused = false;
      unpaused.signalAll();
    } finally {
      pauseLock.unlock();
    }
  }

  private void pausedLoop() throws InterruptedException {
    pauseLock.lock();
    try {
      while (isPaused) {
        synchronized (status) {
          if (statusBeforePause == null) {
            statusBeforePause = status.get();
            setStatus(PAUSED);
          }
        }
        unpaused.await();
      }
    } finally {
      synchronized (status) {
        if (status.get() == PAUSED) {
          setStatus(statusBeforePause);
          statusBeforePause = null;
        }
      }
      pauseLock.unlock();
    }
  }

  public void lockStatus() {
    status.lock();
  }


  public void unlockStatus(long delay) {
    status.unlock(delay, TimeUnit.MILLISECONDS);
  }

  public void unlockStatus() {
    status.unlock();
  }

  public CardTerminal getCardTerminal() {
    cardTerminalLock.lock();
    try {
      return cardTerminal;
    } finally {
      cardTerminalLock.unlock();
    }
  }

  public Card getCurrentCard() {
    return card;
  }

  public ReentrantLock getCardLock() {
    return cardLock;
  }

  public CardRecords getCurrentCardRecords() {
    return cardRecords;
  }

  private void setStatus(Status s) {
    synchronized (status) {
      if (status.get(true) != s) {
        printInfo(status.get(true) + " => " + s);
      }
      status.set(s);
    }
  }

  public LockableValue<Status> statusProperty() {
    return status;
  }


  public Status getStatus() {
    return status.get();
  }

  public BooleanProperty cardPresentProperty() {
    return cardPresent;
  }

  public abstract void readingFinished();

  private void read() throws InterruptedException { //Card is present
    readingAttemptsUntilGiveUp = settings.smartCard.cardReadingAttemptsUntilGiveUp;

    runAction(() -> {
      createCard();
      readCard();
    });
  }

  private void clearCard() {
    cardLock.lock();
    try {
      card = null;
    } finally {
      cardLock.unlock();
    }
  }

  private void createCard() throws CardException {
    cardLock.lock();
    try {
      card = cardTerminal.connect("T=1");
    } finally {
      cardLock.unlock();
    }
    card.beginExclusive();
  }

  private void readCard() throws APDUException, UnsupportedEncodingException, CardException {
    cardRecords = CardRecords.read(settings, handler.getCardRecordsFactories(), card);
    printInfo("Received card data: " + cardRecords);
    setStatus(SUCCESS);

    cardLock.lock();
    try {
      card.endExclusive();
    } finally {
      cardLock.unlock();
    }

    readingFinished();
  }

  private void runAction(Action action) throws InterruptedException { //Card is present
    try {
      action.run();
    } catch (CardException e) {
      if (e.getCause() != null && e.getCause().getClass().getSimpleName().equals("PCSCException")) {
        switch (TerminalsManager.CardErrorType.get(e.getCause().getMessage())) {
          case UNRESPONSIVE_CARD:
            setStatus(UNRESPONSIVE_CARD);
            printInfo("Unresponsive card");
            break;
          case PROTO_MISMATCH:
            setStatus(PROTOCOL_MISMATCH);
            printInfo("Protocol mismatch");
            break;
          default:
            setStatus(FAILED);
            printInfo("Failed " + e.getCause().getMessage());
            cardPresent.set(false);
        }

        readingFinished();
      } else {
        throw new AppQuitException(e);
      }
    } catch (UnsupportedEncodingException e) {
      setStatus(PROTOCOL_MISMATCH);
      printInfo("Failed " + e);
      cardPresent.set(false);
    } catch (APDUException e) {
      if (readingAttemptsUntilGiveUp > 0) {
        readingAttemptsUntilGiveUp--;
        printInfo("Couldn't read card right now. Waiting " + settings.smartCard.cardReadingFailedRetryInterval + "ms... (" + e.getMessage() + ")");
        e.printStackTrace();
        Thread.sleep(settings.smartCard.cardReadingFailedRetryInterval);

        runAction(this::readCard);
      } else {
        // give up on this card
        setStatus(APDU_EXCEPTION);
        printInfo("Giving up on trying to read the card");
        readingFinished();
      }
    }
  }

  public void printInfo(String message) {
    System.out.printf("'%s' (%s): %s%n", cardTerminalName, getStatus(), message);
  }

}
