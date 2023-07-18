package com.knemerzitski.isikreg.beans;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.concurrent.TimeUnit;

import static com.knemerzitski.isikreg.beans.ObservableChanges.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ObservableChangesTest {


  @Test
  public void testValueChange() throws Throwable {
    IntegerProperty prop = new SimpleIntegerProperty(0);

    ObservableChanges<Number> await = observableNewChange(prop).values(1,2,3).get();
    prop.set(1);
    prop.set(2);
    prop.set(2);
    prop.set(3);
    await.await(1, TimeUnit.SECONDS);

    await = observableNewChange(prop).value(4).get();
    ObservableChanges<Number> await2 = observableOldChange(prop).value(3).get();
    prop.set(4);
    await.await(1, TimeUnit.SECONDS);
    await2.await(1, TimeUnit.SECONDS);
  }

  @Test
  public void testNoValueChange() throws Throwable {
    IntegerProperty prop = new SimpleIntegerProperty(0);

    ObservableChanges<Number> await = observableNewChange(prop).value(1).get();
    prop.set(1);
    await.await(1, TimeUnit.SECONDS);

    await = observableNoChange(prop);
    prop.set(2);
    ObservableChanges<Number> finalAwait = await;
    assertThrows(AssertionFailedError.class, () -> {
      finalAwait.await(1, TimeUnit.SECONDS);
    });
  }

  @Test
  public void testOptionalValue() throws Throwable {
    IntegerProperty prop = new SimpleIntegerProperty(0);

    ObservableChanges<Number> await = observableNewChange(prop)
        .value(1)
        .value(2).optional()
        .value(3)
        .get();
    prop.set(1);
    prop.set(2);
    prop.set(3);
    await.await(1, TimeUnit.SECONDS);

    await = observableNewChange(prop)
        .value(4)
        .value(5).optional()
        .value(6)
        .get();
    prop.set(4);
    prop.set(6);
    await.await(1, TimeUnit.SECONDS);

    await = observableNewChange(prop)
        .value(7)
        .value(8).optional()
        .values(9, 10)
        .get();
    prop.set(7);
    prop.set(99);
    prop.set(9);
    prop.set(10);

    ObservableChanges<Number> finalAwait = await;
    assertThrows(AssertionFailedError.class, () -> {
      finalAwait.await(1, TimeUnit.SECONDS);
    });
  }

}
