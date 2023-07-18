package com.knemerzitski.isikreg.beans;

import com.knemerzitski.isikreg.threading.CancellableCountDownLatch;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ObservableChanges<T> {

  public static <G> ObservableChangesBuilder<G> observableNewChange(ObservableValue<G> property) {
    return new ObservableChangesBuilder<G>(property, (ob, o, n, e) -> n == e);
  }

  public static <G> ObservableChangesBuilder<G> observableOldChange(ObservableValue<G> property) {
    return new ObservableChangesBuilder<G>(property, (ob, o, n, e) -> o == e);
  }

  public static <G> ObservableChanges<G> observableNoChange(ObservableValue<G> property) {
    return new ObservableChangesBuilder<G>(property, (ob, o, n, e) -> false).get();
  }

  public static class ObservableChangesBuilder<T> {

    private final ObservableValue<T> property;
    private final ChangeComparator<T> comparator;
    private final List<Value<? extends T>> values = new ArrayList<>();

    private ObservableChangesBuilder(ObservableValue<T> property, ChangeComparator<T> comparator) {
      this.property = property;
      this.comparator = comparator;
    }

    public ObservableChangesBuilder<T> value(T value){
      values.add(new Value<>(value));
      return this;
    }

    public ObservableChangesBuilder<T> values(T...newValues){
      Arrays.stream(newValues).sequential().forEach(this::value);
      return this;
    }

    public ObservableChangesBuilder<T> optional(){
      if(!values.isEmpty()) {
        values.get(values.size()-1).optional();
      }
      return this;
    }

    public ObservableChanges<T> get(){
      return new ObservableChanges<T>(property, comparator, new ArrayList<>(values));
    }
  }

  private static class Value<V> {
    private final V value;
    private boolean optional;

    private Value(V value) {
      this.value = value;
    }

    public Value<V> optional() {
      this.optional = true;
      return this;
    }
  }

  private interface ChangeComparator<G> {
    boolean changed(ObservableValue<? extends G> observable, G oldValue, G newValue, G expected);
  }

  private final AtomicInteger index = new AtomicInteger();
  private final CancellableCountDownLatch latch;

  private Callable<Boolean> afterLastChange;

  private ObservableChanges(ObservableValue<T> property, List<T> expectedValues, ChangeComparator<T> comparator) {
    this(property, comparator, expectedValues.stream().map(Value::new).collect(Collectors.toList()));
  }

  private ObservableChanges(ObservableValue<T> property, ChangeComparator<T> comparator, List<Value<? extends T>> expectedValues) {
    if (expectedValues.isEmpty()) {
      expectedValues.add(new Value<>(null)); // Add an empty value that can be waited for
    }

    latch = new CancellableCountDownLatch(expectedValues.size()) {
      @Override
      public boolean await(long timeout, TimeUnit unit) throws Throwable {
        try {
          return super.await(timeout, unit);
        } catch (InterruptedException e) {
          if (!this.isCancelled()) {
            throw e;
          } else if (this.getException() != null) {
            this.getException().addSuppressed(new Exception());
            throw this.getException();
          }
        }
        return false;
      }
    };
    ObservableChanges<T> thisKlass = this;
    property.addListener(new ChangeListener<T>() {

      private void countDown() {
        index.set(index.get() + 1);
        if (latch.getCount() == 1) {
          synchronized (thisKlass) {
            if (afterLastChange != null) {
              try {
                afterLastChange.call();
              } catch (Throwable e) {
                latch.cancel(e);
              }
            }
          }
        }
        latch.countDown();
        if (latch.getCount() == 0) {
          property.removeListener(this);
        }
      }

      @Override
      public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue) {
        if(latch.getCount() <= 0){ // Should be removed
          property.removeListener(this);
          return;
        }
        if(index.get() < expectedValues.size()){
          Value<? extends T> propVal = expectedValues.get(index.get());
          if (comparator.changed(observable, oldValue, newValue, propVal.value)) {
            countDown();
            return;
          } else if (propVal.optional) {
            countDown();
            changed(observable, oldValue, newValue);
            return;
          }
        }
        latch.cancel(new AssertionFailedError("ObservableValue unexpected change", expectedValues.get(index.get()), oldValue + " => " + newValue));
      }
    });
  }

  public boolean await(long timeout, TimeUnit unit) throws Throwable {
    return latch.await(timeout, unit);
  }

  public synchronized ObservableChanges<T> afterLast(Callable<Boolean> afterLastChange) {
    this.afterLastChange = afterLastChange;
    return this;
  }

}
