package com.knemerzitski.isikreg.threading;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TaskExecutor {

  private final Thread.UncaughtExceptionHandler exceptionHandler;

  private final ThreadFactory threadFactory;

  private final ThreadPoolExecutor taskExecutor;
  private final ScheduledThreadPoolExecutor scheduler;

  public TaskExecutor(Thread.UncaughtExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;

    ThreadFactory defaultFactory = Executors.defaultThreadFactory();
    threadFactory = (runnable) -> {
      Thread t = defaultFactory.newThread(runnable);
      t.setUncaughtExceptionHandler(exceptionHandler);
      return t;
    };

    taskExecutor = new ThreadPoolExecutor(3, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), threadFactory) {
      @Override
      protected void afterExecute(Runnable r, Throwable t) {
        TaskExecutor.this.afterExecute(r, t);
      }
    };

    scheduler = new ScheduledThreadPoolExecutor(2, threadFactory) {
      @Override
      protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        TaskExecutor.this.afterExecute(r, t);
      }
    };
    scheduler.setRemoveOnCancelPolicy(true);
  }


  private void afterExecute(Runnable r, Throwable t) {
    if (t == null && r instanceof Future<?> && ((Future<?>) r).isDone()) {
      try {
        ((Future<?>) r).get();
      } catch (CancellationException ce) {
        // do nothing on cancel
      } catch (ExecutionException ee) {
        t = ee.getCause();
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        t = e;
      }
    }
    if (t != null)
      exceptionHandler.uncaughtException(Thread.currentThread(), t);
  }

  public void execute(Runnable task) {
    taskExecutor.execute(task);
  }

  public <T> Future<T> submit(Callable<T> task) {
    return taskExecutor.submit(task);
  }

  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return scheduler.schedule(callable, delay, unit);
  }

  public List<Runnable> shutdownNow() {
    return Stream.concat(
        taskExecutor.shutdownNow().stream(),
        scheduler.shutdownNow().stream()
    ).collect(Collectors.toList());
  }

  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    long nanos = unit.toNanos(timeout);
    long start = System.nanoTime();
    boolean taskExecutorOk = taskExecutor.awaitTermination(nanos, TimeUnit.NANOSECONDS);
    nanos -= System.nanoTime() - start;
    boolean schedulerOk = scheduler.awaitTermination(nanos, TimeUnit.NANOSECONDS);
    return taskExecutorOk && schedulerOk;
  }

  public boolean isTerminated() {
    return taskExecutor.isTerminated() && scheduler.isTerminated();
  }

  public boolean isStopping() {
    return taskExecutor.isTerminating() || taskExecutor.isTerminated() || taskExecutor.isShutdown() ||
        scheduler.isTerminating() || scheduler.isTerminated() || scheduler.isShutdown();
  }


}
