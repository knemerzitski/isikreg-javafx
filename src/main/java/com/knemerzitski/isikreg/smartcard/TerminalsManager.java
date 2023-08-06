package com.knemerzitski.isikreg.smartcard;


import com.knemerzitski.isikreg.beans.LockableValue;
import com.knemerzitski.isikreg.exception.AppQuitException;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.smartcard.records.CardRecords;
import com.knemerzitski.isikreg.smartcard.records.EstIdCardRecordsV2011;
import com.knemerzitski.isikreg.smartcard.records.EstIdCardRecordsV2018;
import com.knemerzitski.isikreg.smartcard.records.Record;
import com.knemerzitski.isikreg.threading.TaskExecutor;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class TerminalsManager implements Runnable {

  private static final String TERMINAL_FACTORY_TYPE = "PC/SC";
  private static final String TERMINAL_FACTORY_TYPE_NONE = "None";

  private static final long MIN_WAIT_FOR_CHANGE_TIME = 1000; // ms > 0

  public enum CardErrorType {
    NO_READERS_AVAILABLE("SCARD_E_NO_READERS_AVAILABLE"),
    SERVICE_STOPPED("SCARD_E_SERVICE_STOPPED"),
    NO_SERVICE("SCARD_E_NO_SERVICE"),

    READER_UNAVAILABLE("SCARD_E_READER_UNAVAILABLE"),
    NO_SMARTCARD("SCARD_E_NO_SMARTCARD"),
    UNRESPONSIVE_CARD("SCARD_W_UNRESPONSIVE_CARD"),
    REMOVED_CARD("SCARD_W_REMOVED_CARD"),
    PROTO_MISMATCH("SCARD_E_PROTO_MISMATCH"),
    UNKNOWN_ERROR("Unknown error"),
    UNDEFINED_ERROR("Undefined error");

    private final String message;

    CardErrorType(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }

    private static final Map<String, CardErrorType> MAP = Stream.of(values())
        .collect(Collectors.toMap(CardErrorType::getMessage, Function.identity()));

    public static CardErrorType get(String message) {
      CardErrorType err = MAP.get(message);
      return err != null ? err : UNDEFINED_ERROR;
    }
  }

  public enum Status {
    NULL,
    INIT,
    DRIVER_MISSING,
    PAUSED,
    LOCKED,
    WAITING_CARD_READER,
    WAITING_CARD,
    READING_CARD,
    PROCESSING_CARD,
    UNRESPONSIVE_CARD,
    APDU_EXCEPTION,
    PROTOCOL_MISMATCH,
    FAILED,
    SUCCESS
  }


  final Settings settings;
  final TaskExecutor taskExecutor;

  private final TerminalFactory terminalFactory;

  private final HashMap<Class<? extends CardRecords>, Map<Column, Supplier<Record<?>>>> cardRecordsFactories;

  // All card reader terminals
  private CardTerminals terminals;

  private final ConcurrentMap<String, TerminalReader> cardTerminalReaders = new ConcurrentSkipListMap<>();

  // Pause
  private boolean isPaused;
  private final ReentrantLock pauseLock = new ReentrantLock();
  private final Condition unpaused = pauseLock.newCondition();
  private Status statusBeforePause;

  // Card records processing
  private boolean processingCardRecords = false;
  private final ReentrantLock processCardRecordsLock = new ReentrantLock();
  private final Condition processingCardRecordsDone = processCardRecordsLock.newCondition();


  // Current status
  private final LockableValue<Status> status;
  private final ProcessedReadersManager processedReadersManager;
  private final BooleanProperty cardPresent = new SimpleBooleanProperty();

  public TerminalsManager(Settings settings, TaskExecutor taskExecutor) {
    this(settings, TerminalFactory.getDefault(), taskExecutor);
  }

  public TerminalsManager(Settings settings, TerminalFactory terminalFactory, TaskExecutor taskExecutor) {
    this.terminalFactory = terminalFactory;
    this.settings = settings;
    this.taskExecutor = taskExecutor;

    processedReadersManager = new ProcessedReadersManager(this);
    this.cardRecordsFactories = createCardRecordsFactories();

    status = new LockableValue<>(taskExecutor, Status.INIT, Status.LOCKED);

    System.out.printf("Terminal factory type: '%s'%n", terminalFactory.getType());
    if (terminalFactory.getType().equals(TERMINAL_FACTORY_TYPE_NONE)) {
      setStatusUnsafe(Status.DRIVER_MISSING);
      return;
    }
  }

  private HashMap<Class<? extends CardRecords>, Map<Column, Supplier<Record<?>>>> createCardRecordsFactories() {
    HashMap<Class<? extends CardRecords>, Map<Column, Supplier<Record<?>>>> factories = new HashMap<>();

    factories.put(EstIdCardRecordsV2018.class, EstIdCardRecordsV2018.createRecordsFactory(settings));
    factories.put(EstIdCardRecordsV2011.class, EstIdCardRecordsV2011.createRecordsFactory(settings));

    return factories;
  }

  /**
   * Call successReaderSignal() when you're done processing card records.
   * Handler will block until successReaderSignal() is called. Meant for asynchronous code.
   *
   * @param reader Processed reader
   */
  protected abstract void successReader(ProcessedReader reader);

  protected abstract void failedReader(ProcessedReader reader);

  @Override
  public void run() {
    // Cannot proceed without PC/SC TerminalFactory
    if(terminalFactory.getType().equals(TERMINAL_FACTORY_TYPE_NONE)) return;

    terminals = terminalFactory.terminals();

    try {
      taskExecutor.execute(processedReadersManager);
    } catch (RejectedExecutionException e) {
      // Normal to get rejected exception only when scheduler is stopping
      if (!taskExecutor.isStopping()) {
        throw e;
      }
    }


    Thread thread = Thread.currentThread();
    try {
      while (!thread.isInterrupted()) {
        process();
      }
    } catch (InterruptedException e) {
      if (!taskExecutor.isStopping())
        throw new AppQuitException(e); // Unexpected interrupt
    }
  }

  private void process() throws InterruptedException {
    pausedLoop();
    try {
      processTerminals();
      waitForChange();
    } catch (CardException e) {
      if (e.getCause() != null && e.getCause().getClass().getSimpleName().equals("PCSCException")) {
        switch (CardErrorType.get(e.getCause().getMessage())) {
          case NO_READERS_AVAILABLE:
            waitForCardReader(settings.smartCard.noReadersCheckInterval);
            break;
          case NO_SERVICE:
          case SERVICE_STOPPED:
            initNewTerminalsContext();
            break;
          default:
            throw new AppQuitException(e);
        }
      } else {
        throw new AppQuitException(e);
      }
    }
  }

  private void waitForChange() throws CardException, InterruptedException {
    boolean missingTerminals = cardTerminalReaders.values().stream().anyMatch(r -> r.getCardTerminal() == null);
    long maxWaitTime = missingTerminals ?
        settings.smartCard.readerMissingCheckInterval :
        settings.smartCard.readersPresentCheckInterval;
    long start = System.currentTimeMillis();
    long waitTime = Math.max(MIN_WAIT_FOR_CHANGE_TIME, settings.smartCard.waitForChangeLoopInterval);
    while (!terminals.waitForChange(waitTime)) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException("Waiting for terminals change interrupted");
      }
      if (System.currentTimeMillis() - start >= maxWaitTime)
        break;
    }
  }

  /**
   * Sleeps timeout amount and remembers that card reader is not available
   */
  private void waitForCardReader(long timeout) throws InterruptedException {
    if (status.get() != Status.WAITING_CARD_READER) {
      System.out.println("No readers available. Waiting for card reader...");
      setStatus(Status.WAITING_CARD_READER);
    }

    Thread.sleep(timeout);
  }


  private void processTerminals() throws CardException {
    List<CardTerminal> allTerminals = terminals.list();

    // Create separate thread for each new terminal
    for (CardTerminal newTerminal : allTerminals) {
      TerminalReader existingReader = cardTerminalReaders.get(newTerminal.getName());
      if (existingReader == null) {
        TerminalReader newReader = newReader(newTerminal);
        cardTerminalReaders.put(newTerminal.getName(), newReader);
        try {
          taskExecutor.execute(newReader);
        } catch (RejectedExecutionException e) {
          // Normal to get rejected exception only when taskExecutor is stopping
          if (!taskExecutor.isStopping()) {
            throw e;
          }
        }
      } else {
        if (existingReader.getCardTerminal() == null) {
          // Update existing reader with new terminal
          existingReader.setCardTerminal(newTerminal);
        }
      }
    }
  }

  protected TerminalReader newReader(CardTerminal newTerminal) {
    TerminalReader reader = new TerminalReader(this, newTerminal) {
      @Override
      public void readingFinished() {
        processedReadersManager.offer(new ProcessedReader(this));
      }
    };
    reader.statusProperty().addListener((observable, oldValue, s) -> {
      updateStatusFromReaders();
    });
    return reader;
  }

  protected void updateStatusFromReaders() {
    if (!processedReadersManager.getProcessingLock().tryLock()) {
      return;
    }
    try {
      synchronized (status) {
        if (!processedReadersManager.isEmpty())
          return;
        Collection<TerminalReader> readers = cardTerminalReaders.values();
        boolean reading = readers.stream().anyMatch(r -> r.getStatus() == Status.READING_CARD);
        if (reading) {
          setStatusUnsafe(Status.READING_CARD);
        } else {
          boolean locked = readers.stream().anyMatch(r -> r.statusProperty().isLocked());
          if (locked)
            return;

          // Success status becomes locked eventually but there can be a slight delay
          boolean toBeProcessed = readers.stream().anyMatch(r -> r.getStatus() == Status.SUCCESS);
          if(toBeProcessed){
            return; // Don't show waiting card if there is a card already successfully read
          }

          boolean waiting = readers.stream().anyMatch(r -> r.getStatus() == Status.WAITING_CARD);

          if (waiting) {
            setStatusUnsafe(Status.WAITING_CARD);
          }
        }
      }
    } finally {
      processedReadersManager.getProcessingLock().unlock();
    }
  }

  protected void startProcessingCardRecords() {
    processCardRecordsLock.lock();
    try {
      processingCardRecords = true;
    } finally {
      processCardRecordsLock.unlock();
    }
  }

  protected void successReaderSignal() {
    processCardRecordsLock.lock();
    try {
      processingCardRecords = false;
      processingCardRecordsDone.signalAll();
    } finally {
      processCardRecordsLock.unlock();
    }
  }

  protected void awaitProcessingCardRecordsDone() throws InterruptedException {
    processCardRecordsLock.lock();
    try {
      while (processingCardRecords) {
        processingCardRecordsDone.await();
      }
    } finally {
      processCardRecordsLock.unlock();
    }
  }

  public boolean isPaused() {
    return isPaused;
  }

  public void pauseRequest() {
    pauseLock.lock();
    try {
      isPaused = true;
      processedReadersManager.pauseRequest();
      cardTerminalReaders.forEach((s, r) -> r.pauseRequest());
    } finally {
      pauseLock.unlock();
    }
  }

  public void resumeRequest() {
    pauseLock.lock();
    try {
      isPaused = false;
      processedReadersManager.resumeRequest();
      cardTerminalReaders.forEach((s, r) -> r.resumeRequest());
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
            setStatusUnsafe(Status.PAUSED);
          }
        }
        unpaused.await();
      }
    } finally {
      synchronized (status) {
        if (status.get() == Status.PAUSED) {
          setStatusUnsafe(statusBeforePause);
          statusBeforePause = null;
        }
      }
      pauseLock.unlock();
    }
  }

  private void updateCardPresentBoundCheck() {
    synchronized (cardPresent) {
      if (cardPresent.isBound())
        return;
      switch (status.get(true)) {
        case WAITING_CARD_READER:
        case WAITING_CARD:
        case FAILED:
          cardPresent.set(false);
          break;
        case READING_CARD:
        case PROCESSING_CARD:
          cardPresent.set(true);
          break;
      }
    }
  }


  protected void setStatusUnsafe(Status s) {
    if (status.get(true) != s) {
      System.out.printf("'All terminals' (%s): %s => %s%n", status.get(true), status.get(), s);
    }
    status.set(s);
    updateCardPresentBoundCheck();
  }

  public LockableValue<Status> statusProperty() {
    return status;
  }

  public Status getStatus() {
    return status.get();
  }

  protected void setStatus(Status s) {
    synchronized (status) {
      setStatusUnsafe(s);
    }
  }

  public BooleanProperty cardPresentProperty() {
    return cardPresent;
  }

  public HashMap<Class<? extends CardRecords>, Map<Column, Supplier<Record<?>>>> getCardRecordsFactories() {
    return cardRecordsFactories;
  }

  public TerminalReader getCardTerminalReader(String name) {
    return cardTerminalReaders.get(name);
  }

  /**
   * Every time card reader is removed from the system, new terminal context must be created.
   */
  private void initNewTerminalsContext() {
    try {
      System.out.println("Initializing new terminals context");
      Class<?> PCSCTerminals = Class.forName("sun.security.smartcardio.PCSCTerminals");
      Field contextId = PCSCTerminals.getDeclaredField("contextId");
      contextId.setAccessible(true);

      if (contextId.getLong(PCSCTerminals) != 0) {
        Class<?> PCSC = Class.forName("sun.security.smartcardio.PCSC");
        Method SCardEstablishContext = PCSC.getDeclaredMethod("SCardEstablishContext", Integer.TYPE);
        SCardEstablishContext.setAccessible(true);

        Field SCARD_SCOPE_USER = PCSC.getDeclaredField("SCARD_SCOPE_USER");
        SCARD_SCOPE_USER.setAccessible(true);

        long newContextId = (Long) SCardEstablishContext.invoke(PCSC, new Object[]{SCARD_SCOPE_USER.getInt(PCSC)});
        contextId.setLong(PCSCTerminals, newContextId);

        this.terminals = TerminalFactory.getInstance(TERMINAL_FACTORY_TYPE, null).terminals();
        Field fieldTerminals = PCSCTerminals.getDeclaredField("terminals");
        fieldTerminals.setAccessible(true);
        Class<?> classMap = Class.forName("java.util.Map");
        Method clearMap = classMap.getDeclaredMethod("clear");
        clearMap.invoke(fieldTerminals.get(terminals));
      }
    } catch (Exception e) {
      throw new AppQuitException(e);
    }
  }

}
