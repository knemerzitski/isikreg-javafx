package com.knemerzitski.isikreg.utils;

import java.util.List;

public class ByteUtils {

  private ByteUtils() {
  }

  public static String toHexString(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X ", b));
    }
    return sb.toString();
  }

  public static String toHexString(List<Byte> bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X ", b));
    }
    return sb.toString();
  }


}
