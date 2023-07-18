package com.knemerzitski.isikreg.extensions;

import com.knemerzitski.isikreg.threading.TaskExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.fail;

public class TaskExecutorTestExtension {

  private TaskExecutor taskExecutor;
  private ConcurrentLinkedQueue<Throwable> taskExecutorExceptions;

  protected TaskExecutor getTaskExecutor() {
    return taskExecutor;
  }

  @BeforeEach
  public void setupThis() throws IOException {
    taskExecutorExceptions = new ConcurrentLinkedQueue<>();
    taskExecutor = new TaskExecutor((t, e) -> {
      taskExecutorExceptions.offer(e);
    });
  }

  @AfterEach
  public void tearThis() {
    taskExecutor.shutdownNow();

    Throwable t;
    while ((t = taskExecutorExceptions.poll()) != null) {
      fail(t);
    }
  }



}
