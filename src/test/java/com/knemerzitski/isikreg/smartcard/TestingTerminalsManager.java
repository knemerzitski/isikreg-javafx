package com.knemerzitski.isikreg.smartcard;

import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.threading.TaskExecutor;

import javax.smartcardio.TerminalFactory;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestingTerminalsManager extends TerminalsManager {

  private final LinkedBlockingDeque<ProcessedReader> results;

  public TestingTerminalsManager(Settings settings, TerminalFactory terminalFactory, TaskExecutor taskExecutor) {
    super(settings, terminalFactory, taskExecutor);
    results = new LinkedBlockingDeque<>();
  }

  private void processReader(ProcessedReader reader) {
    results.offer(reader);
    if (reader.getRecords() != null) {
      successReaderSignal();
    }
  }

  public ProcessedReader waitForResult(long timeout, TimeUnit unit) throws InterruptedException {
    ProcessedReader result = results.poll(timeout, unit);
    assertNotNull(result, "No processed reader found!");
    return result;
  }

  @Override
  protected void successReader(ProcessedReader reader) {
    processReader(reader);
  }

  @Override
  protected void failedReader(ProcessedReader reader) {
    processReader(reader);
  }
}
