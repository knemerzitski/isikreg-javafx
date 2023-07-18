package com.knemerzitski.isikreg.smartcard;

public class APDUException extends Exception {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  private final int status;

  public APDUException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int getStatus() {
    return status;
  }

}
