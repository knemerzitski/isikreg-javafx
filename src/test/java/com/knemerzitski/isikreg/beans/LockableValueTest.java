package com.knemerzitski.isikreg.beans;

import com.knemerzitski.isikreg.extensions.TaskExecutorTestExtension;
import javafx.beans.value.ChangeListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class LockableValueTest extends TaskExecutorTestExtension {

  @Test
  public void testDefaultValue() {
    LockableValue<String> lockableValue = new LockableValue<>(getTaskExecutor(), "DEFAULT_VALUE", "LOCKED");

    assertFalse(lockableValue.isLocked());
    assertEquals("DEFAULT_VALUE", lockableValue.get());
    assertFalse(lockableValue.isLocked());
  }

  @Test
  public void testValueChanges() {
    LockableValue<String> lockableValue = new LockableValue<>(getTaskExecutor(), "DEFAULT_VALUE", "LOCKED");

    lockableValue.set("HI");

    assertEquals("HI", lockableValue.get());
  }

  @Test
  public void testValueChangesWhileLocked() {
    LockableValue<String> lockableValue = new LockableValue<>(getTaskExecutor(), "DEFAULT_VALUE", "LOCKED");

    lockableValue.set("HI");

    lockableValue.lock();
    assertEquals("LOCKED", lockableValue.get());
    assertEquals("HI", lockableValue.get(true));

    lockableValue.set("HI2");
    assertEquals("LOCKED", lockableValue.get());
    assertEquals("HI2", lockableValue.get(true));
  }

  @Test
  public void testLocked() {
    LockableValue<String> lockableValue = new LockableValue<>(getTaskExecutor(), "DEFAULT_VALUE", "LOCKED");

    lockableValue.set("HI");

    assertFalse(lockableValue.isLocked());
    lockableValue.lock();
    assertTrue(lockableValue.isLocked());
    assertEquals("LOCKED", lockableValue.get());
    assertEquals("HI", lockableValue.get(true));
  }

  @Test
  public void testUnlockDelayWithListener() throws ExecutionException, InterruptedException, TimeoutException {
    LockableValue<String> lockableValue = new LockableValue<>(getTaskExecutor(), "DEFAULT_VALUE", "LOCKED");

    lockableValue.lock();

    CompletableFuture<Boolean> lockedFuture = new CompletableFuture<>();
    lockableValue.addLockListener((o, n, locked) -> lockedFuture.complete(locked));
    assertTrue(lockableValue.isLocked());

    lockableValue.unlock(10, TimeUnit.MILLISECONDS);
    assertTrue(lockableValue.isLocked());
    assertFalse(lockedFuture.get(50, TimeUnit.MILLISECONDS));
    assertFalse(lockableValue.isLocked());
  }


  @Test
  public void testBindLock() throws ExecutionException, InterruptedException, TimeoutException {
    LockableValue<String> bindThisValue = new LockableValue<>(getTaskExecutor(), "DEFAULT_VALUE", "LOCKED");
    LockableValue<String> otherValue = new LockableValue<>(getTaskExecutor(), "DEFAULT_VALUE2", "LOCKED2");

    bindThisValue.set("BOUND_VALUE");
    otherValue.set("OTHER_VALUE");

    bindThisValue.lock(); // Lock will be same once bound to otherValue

    bindThisValue.bindLocking(otherValue);

    // Check state is correct
    assertFalse(bindThisValue.isLocked());
    assertFalse(otherValue.isLocked());
    assertTrue(bindThisValue.isLockingBound());
    assertFalse(otherValue.isLockingBound());
    assertEquals("BOUND_VALUE", bindThisValue.get());
    assertEquals("OTHER_VALUE", otherValue.get());

    // bindThisValue cannot be bound twice
    assertThrows(IllegalStateException.class, () -> {
      bindThisValue.bindLocking(otherValue);
    }, "Value was bound twice? Shouldn't be allowed");

    assertThrows(IllegalStateException.class, bindThisValue::lock, "It shouldn't be possible to lock a bound value");

    // Check state is correct
    assertFalse(bindThisValue.isLocked());
    assertFalse(otherValue.isLocked());
    assertTrue(bindThisValue.isLockingBound());
    assertFalse(otherValue.isLockingBound());

    // Lock other value
    otherValue.lock();

    // Check state is correct
    assertTrue(bindThisValue.isLocked());
    assertTrue(otherValue.isLocked());
    assertTrue(bindThisValue.isLockingBound());
    assertFalse(otherValue.isLockingBound());
    assertEquals("LOCKED", bindThisValue.get());
    assertEquals("LOCKED2", otherValue.get());

    // Unlock with delay
    CompletableFuture<Boolean> lockedFuture = new CompletableFuture<>();
    otherValue.addLockListener((o, n, locked) -> lockedFuture.complete(locked));
    assertTrue(otherValue.isLocked());

    otherValue.unlock(10, TimeUnit.MILLISECONDS);
    assertTrue(otherValue.isLocked());
    assertTrue(bindThisValue.isLocked());
    assertFalse(lockedFuture.get(50, TimeUnit.MILLISECONDS));
    assertFalse(otherValue.isLocked());
    assertFalse(bindThisValue.isLocked());

    // Unbind, Toby is free~~
    bindThisValue.unbindLocking();
    otherValue.lock();

    assertFalse(bindThisValue.isLocked());
    assertTrue(otherValue.isLocked());
    assertFalse(bindThisValue.isLockingBound());
    assertFalse(otherValue.isLockingBound());
  }


  @Test
  public void testLockingListener() throws ExecutionException, InterruptedException, TimeoutException {
    LockableValue<String> lockableValue = new LockableValue<>(getTaskExecutor(), "DEFAULT_VALUE", "LOCKED");

    AtomicInteger counter = new AtomicInteger(0);
    ChangeListener<Boolean> listener = (o, n, locked) -> counter.set(counter.get() + 1);

    lockableValue.addLockListener(listener);

    assertEquals(0, counter.get());
    lockableValue.lock();
    assertEquals(1, counter.get());
    lockableValue.lock();
    assertEquals(1, counter.get());
    lockableValue.unlock();
    assertEquals(2, counter.get());
    lockableValue.unlock();
    assertEquals(2, counter.get());
    lockableValue.lock();
    lockableValue.unlock();
    lockableValue.lock();
    lockableValue.unlock();
    assertEquals(6, counter.get());

    lockableValue.removeLockListener(listener);
    assertEquals(6, counter.get());
    lockableValue.lock();
    lockableValue.unlock();
    assertEquals(6, counter.get());
  }

  @Test
  public void testValueListener() throws ExecutionException, InterruptedException, TimeoutException {
    LockableValue<String> lockableValue = new LockableValue<>(getTaskExecutor(), "DEFAULT_VALUE", "LOCKED");

    lockableValue.set("OK");

    AtomicInteger counter = new AtomicInteger(0);
    ChangeListener<String> listener = (o, n, val) -> counter.set(counter.get() + 1);

    lockableValue.addListener(listener);

    lockableValue.set("OK1");
    assertEquals(1, counter.get());
    lockableValue.lock();
    lockableValue.set("OK2");
    assertEquals(1, counter.get());
    lockableValue.unlock();
    assertEquals(2, counter.get());
    lockableValue.lock();
    lockableValue.set("OK3");
    lockableValue.set("OK4");
    assertEquals(2, counter.get());
    lockableValue.unlock();
    assertEquals(3, counter.get());
    assertEquals("OK4", lockableValue.get());

    lockableValue.removeListener(listener);
    assertEquals(3, counter.get());
    lockableValue.set("OK5");
    lockableValue.lock();
    lockableValue.set("OK6");
    lockableValue.unlock();
    lockableValue.set("OK7");
    assertEquals(3, counter.get());
    assertEquals("OK7", lockableValue.get());

  }

}
