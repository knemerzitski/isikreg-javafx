package com.knemerzitski.isikreg.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ArrayTestUtils {

  public static boolean equals(byte[] arr1, int offset1, byte[] arr2, int offset2, int len) {
    if (offset1 + len > arr1.length ||
        offset2 + len > arr2.length) return false;

    for (int i = 0; i < len; i++) {
      if (arr1[offset1 + i] != arr2[offset2 + i]) return false;
    }
    return true;
  }

  public static boolean startsWith(byte[] arr, byte[] arr2) {
    if (arr.length < arr2.length) return false;
    for (int i = 0; i < arr2.length; i++) {
      if (arr[i] != arr2[i]) return false;
    }
    return true;
  }

  @Test
  public void testEquals() {
    byte[] arr1 = new byte[]{1, 2, 3, 4, 5, 6, 7};
    byte[] arr2 = new byte[]{5, 9, 12, 4, 3, 43, 7, 6, 2, 3, 4, 5, 6, 66};

    assertTrue(ArrayTestUtils.equals(arr1, 1, arr2, 8, 3));
    assertFalse(ArrayTestUtils.equals(arr1, 5, arr2, 11, 4));
  }

  @Test
  public void testEquals2() {
    byte[] arr1 = new byte[]{0x00, (byte) 0xb2, 0x04, 0x00};
    byte[] arr2 = new byte[]{0x00, (byte) 0xb2, 0x05, 0x04, 0x00};

    assertTrue(ArrayTestUtils.equals(arr1, 0, arr2, 0, 2));
    assertTrue(ArrayTestUtils.equals(arr1, 2, arr2, 3, 2));
    assertEquals(arr2[2], 0x05);
  }

  private ArrayTestUtils() {
  }
}
