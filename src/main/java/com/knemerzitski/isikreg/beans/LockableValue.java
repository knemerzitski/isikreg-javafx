package com.knemerzitski.isikreg.beans;

import com.knemerzitski.isikreg.threading.TaskExecutor;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LockableValue<T> implements ObservableValue<T> {

  private final TaskExecutor scheduler;
  private ScheduledFuture<Boolean> delayedUnlockTask;

  private final ObjectProperty<T> value = new SimpleObjectProperty<>();
  private T latestValue;

  private final T lockedValue;
  private final ReadOnlyBooleanWrapper locked = new ReadOnlyBooleanWrapper(false);

  private LockableValue<T> boundToLock;
  private ChangeListener<Boolean> boundToListener;

  public LockableValue(@NotNull TaskExecutor scheduler, @NotNull T defaultValue, T lockedValue) {
    this.scheduler = scheduler;
    value.set(defaultValue);
    this.lockedValue = lockedValue;
  }

  public synchronized void lock() {
    if (isLockingBound()) {
      throw new IllegalStateException("Value is bound. Cannot lock! Unbind the value first.");
    }
    _lock();
  }

  private synchronized void _lock() {
    clearCurrentDelayedUnlock();
    locked.set(true);
  }


  public synchronized void unlock() {
    if (isLockingBound()) {
      throw new IllegalStateException("Value is bound. Cannot unlock! Unbind the value first.");
    }
    _unlock();
  }

  private synchronized void _unlock() {
    clearCurrentDelayedUnlock();
    locked.set(false);
    value.set(latestValue); // Update value with latest value and trigger listener changes
  }

  public synchronized void set(T value) {
    latestValue = value;
    if (!isLocked()) {
      this.value.set(value);
    }
  }

  public synchronized T get() {
    return get(false);
  }

  public synchronized T get(boolean ignoreLock) {
    if (isLocked() && !ignoreLock) {
      return lockedValue;
    } else if (isLocked()) { // ignoreLock = true
      return latestValue;
    } else {
      return value.get();
    }
  }

  public synchronized void unlock(long delay, TimeUnit unit) {
    clearCurrentDelayedUnlock();

    try {
      delayedUnlockTask = scheduler.schedule(() -> {
        synchronized (this) {
          if (delayedUnlockTask != null && !delayedUnlockTask.isCancelled()) {
            unlock();
          }
          return true;
        }
      }, delay, unit);
    } catch (RejectedExecutionException e) {
      // Normal to get rejected exception only when scheduler is stopping
      if (!scheduler.isStopping()) {
        throw e;
      }
    }
  }

  @Override
  public synchronized void addListener(ChangeListener<? super T> listener) {
    value.addListener(listener);
  }

  @Override
  public synchronized void removeListener(ChangeListener<? super T> listener) {
    value.removeListener(listener);
  }

  public synchronized void addLockListener(ChangeListener<Boolean> listener) {
    locked.addListener(listener);
  }

  public synchronized void removeLockListener(ChangeListener<Boolean> listener) {
    locked.removeListener(listener);
  }

  public synchronized void bindLocking(LockableValue<T> otherState) {
    if (isLockingBound()) {
      throw new IllegalStateException("This state is already bound!");
    }
    boundToLock = otherState;
    boundToListener = (o, n, otherLocked) -> {
      if (otherLocked) {
        _lock();
      } else {
        _unlock();
      }
    };
    boundToListener.changed(otherState.locked, otherState.locked.get(), otherState.locked.get());
    otherState.addLockListener(boundToListener);
    // This value can no longer be locked/unlocked by itself
  }

  public synchronized void unbindLocking() {
    if (!isLockingBound()) return;
    boundToLock.removeLockListener(boundToListener);
    boundToLock = null;
    boundToListener = null;
  }

  public synchronized boolean isLockingBound() {
    return boundToLock != null;
  }

  public synchronized boolean isLocked() {
    return locked.get();
  }

  private void clearCurrentDelayedUnlock() {
    if (delayedUnlockTask != null) {
      delayedUnlockTask.cancel(true);
      delayedUnlockTask = null;
    }
  }


  @Override
  public synchronized T getValue() {
    return value.getValue();
  }

  @Override
  public synchronized void addListener(InvalidationListener listener) {
    value.addListener(listener);
  }

  @Override
  public synchronized void removeListener(InvalidationListener listener) {
    value.removeListener(listener);
  }

  public ObservableValue<Boolean> lockProperty(){
    return locked.getReadOnlyProperty();
  }
}
