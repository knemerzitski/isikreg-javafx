package com.knemerzitski.isikreg.threading;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Await {

  private final ReentrantLock lock = new ReentrantLock();
  private final Condition condition = lock.newCondition();
  private boolean awaiting;

  public void await() throws InterruptedException {
    lock.lock();
    try {
      while (awaiting) {
        condition.await();
      }
    } finally {
      lock.unlock();
    }
  }

  public boolean await(long time, TimeUnit unit) throws InterruptedException {
    lock.lock();
    try {
      while (awaiting) {
        if(!condition.await(time, unit)){
          return false;
        }
      }
      return true;
    } finally {
      lock.unlock();
    }
  }

  public void setAwaiting(boolean awaiting) {
    lock.lock();
    try {
      this.awaiting = awaiting;
      if (!this.awaiting) condition.signalAll();
    } finally {
      lock.unlock();
    }
  }


}
