package com.knemerzitski.isikreg.person;

public interface ProgressListener {

  void start();

  /**
   * @param percent Value between 0 and 1. If value is -1 then progress is indeterminable.
   */
  void progress(double percent);

  void stop();

}
