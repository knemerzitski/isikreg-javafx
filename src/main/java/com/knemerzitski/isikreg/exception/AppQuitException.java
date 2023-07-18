package com.knemerzitski.isikreg.exception;

public class AppQuitException extends RuntimeException {

  public AppQuitException(String message) {
    super(message);
  }

  public AppQuitException(Throwable cause) {
    super(cause);
  }

}
