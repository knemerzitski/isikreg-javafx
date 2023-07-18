package com.knemerzitski.isikreg.smartcard;

import com.knemerzitski.isikreg.exception.AppQuitException;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.threading.Await;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.jetbrains.annotations.NotNull;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import static com.knemerzitski.isikreg.smartcard.TerminalsManager.Status.*;

public class ProcessedReadersManager implements Runnable {

  private final Settings settings;
  private final TerminalsManager handler;

  // Pause
  private final Await pauseAwait = new Await();

  private final LinkedBlockingQueue<ProcessedReader> readers = new LinkedBlockingQueue<>();

  private final ReentrantLock processingLock = new ReentrantLock();

  public ProcessedReadersManager(TerminalsManager handler) {
    this.settings = handler.settings;

    this.handler = handler;
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

    if (processingLock.isLocked() && processingLock.isHeldByCurrentThread())
      processingLock.unlock();
    if (readers.isEmpty()) {
      handler.updateStatusFromReaders();
    }
    ProcessedReader reader = readers.take();
    if (!processingLock.isLocked() || !processingLock.isHeldByCurrentThread())
      processingLock.lock();
    pausedLoop();
    processCardTerminalReader(reader);
  }

  public ReentrantLock getProcessingLock() {
    return processingLock;
  }


  public boolean isEmpty() {
    return readers.isEmpty();
  }

  protected void offer(@NotNull ProcessedReader reader) {
    readers.remove(reader);
    readers.offer(reader);
  }

  protected void processCardTerminalReader(ProcessedReader processedReader) throws InterruptedException {
    if (processedReader.cardChanged()) {
      return;
    }

    TerminalReader reader = processedReader.getReader();

    handler.statusProperty().unbindLocking();
    handler.statusProperty().bindLocking(reader.statusProperty());

    handler.cardPresentProperty().unbind();
    handler.cardPresentProperty().bind(reader.cardPresentProperty());

    ChangeListener<Boolean> readerLockListener = new ChangeListener<Boolean>() {
      @Override
      public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean locked) {
        if (locked) return;
        reader.statusProperty().removeLockListener(this);

        if (processedReader.cardChanged()) return;

        handler.statusProperty().unbindLocking();
        handler.cardPresentProperty().unbind();
      }
    };
    reader.statusProperty().addLockListener(readerLockListener);

    handler.setStatus(PROCESSING_CARD);

    processedReader.getReader().printInfo("Processing...");
    switch (reader.getStatus()) {
      case FAILED:
        handler.setStatus(FAILED);
        break;
      case UNRESPONSIVE_CARD:
        handler.setStatus(UNRESPONSIVE_CARD);
        break;
      case PROTOCOL_MISMATCH:
        handler.setStatus(PROTOCOL_MISMATCH);
        break;
      case APDU_EXCEPTION:
        handler.setStatus(APDU_EXCEPTION);
        break;
      case SUCCESS:
        reader.lockStatus();
        handler.startProcessingCardRecords();
        handler.successReader(processedReader);
        handler.awaitProcessingCardRecordsDone();
        if (settings.smartCard.showSuccessStatusDuration >= 0) {
          reader.unlockStatus(settings.smartCard.showSuccessStatusDuration);
        }
        break;
    }

    if (!processedReader.hasRecords()) {
      handler.failedReader(processedReader);
    }

    // Wait for card to be removed before processing next one
    reader.getCardLock().lock();
    try {
      CardTerminal terminal = reader.getCardTerminal();
      if (terminal != null) {
        if (terminal.isCardPresent() && !processedReader.cardChanged()) {
          processedReader.getReader().printInfo("Waiting card to be removed before processing next reader");
          while (!terminal.waitForCardAbsent(settings.smartCard.waitForChangeLoopInterval)) {
            if (Thread.currentThread().isInterrupted()) {
              throw new InterruptedException("Waiting for card absent interrupted");
            }
            if (!terminal.isCardPresent()) break;
          }
        }
      }
    } catch (CardException e) {
      processedReader.getReader().printInfo("Waiting for card to be removed interrupted. Exception happened");
    } finally {
      reader.statusProperty().removeLockListener(readerLockListener);
      handler.cardPresentProperty().unbind();
      handler.cardPresentProperty().set(false);
      reader.getCardLock().unlock();
    }

    processedReader.getReader().printInfo("Processing done");
  }

  public void pauseRequest() {
    pauseAwait.setAwaiting(true);
  }

  public void resumeRequest() {
    pauseAwait.setAwaiting(false);
  }

  private void pausedLoop() throws InterruptedException {
    pauseAwait.await();
  }


}
