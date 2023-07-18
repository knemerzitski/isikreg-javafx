package com.knemerzitski.isikreg.threading;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

//Source https://stackoverflow.com/questions/10453876/how-do-i-cancel-a-countdownlatch
public class CancellableCountDownLatch {

  private final CountDownLatch latch;
  private final List<Thread> waiters;
  private boolean cancelled;
  private Throwable throwable;

  public CancellableCountDownLatch(int count) {
    this.latch = new CountDownLatch(count);
    this.waiters = new ArrayList<>();
  }

  public synchronized void cancel(){
    cancel(null);
  }

  public synchronized void cancel(Throwable throwable){
    if(!cancelled){
      cancelled = true;
      waiters.forEach(Thread::interrupt);
      waiters.clear();
      this.throwable = throwable;
    }
  }

  public synchronized boolean isCancelled() {
    return cancelled;
  }

  public Throwable getException(){
    return throwable;
  }

  private synchronized void addWaiter() throws InterruptedException {
    if(cancelled){
      Thread.currentThread().interrupt();
      throw new InterruptedException("Latch has already been cancelled");
    }
    waiters.add(Thread.currentThread());
  }

  private synchronized void removeWaiter(){
    waiters.remove(Thread.currentThread());
  }

  public boolean await(long timeout, TimeUnit unit) throws Throwable {
    try{
      addWaiter();
      return latch.await(timeout, unit);
    } finally {
      removeWaiter();
    }
  }

  public void countDown() {
    latch.countDown();
  }

  public long getCount() {
    return latch.getCount();
  }

  @Override
  public String toString() {
    return latch.toString();
  }
}
