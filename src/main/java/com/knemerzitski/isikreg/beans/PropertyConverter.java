package com.knemerzitski.isikreg.beans;

import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

public interface PropertyConverter<A, B> {

  B convertA(A a);

  A convertB(B b);

  static <A, B> void bindBidirectional(Property<A> pa, Property<B> pb, PropertyConverter<A, B> converter) {

    pa.addListener(new ChangeListener<A>() {
      private boolean changing;

      @Override
      public void changed(ObservableValue<? extends A> observable, A oldValue, A newValue) {
        if (!changing) {
          changing = true;
          try {
            pb.setValue(converter.convertA(newValue));
          } finally {
            changing = false;
          }
        }

      }
    });
    pb.addListener(new ChangeListener<B>() {
      private boolean changing;

      @Override
      public void changed(ObservableValue<? extends B> observable, B oldValue, B newValue) {
        if (!changing) {
          changing = true;
          try {
            pa.setValue(converter.convertB(newValue));
          } finally {
            changing = false;
          }
        }
      }
    });
  }


}
