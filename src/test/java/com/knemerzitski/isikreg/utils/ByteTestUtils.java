package com.knemerzitski.isikreg.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ByteTestUtils {

  @Test
  public void testBytesReadable(){
    assertEquals("900 B", getReadableBytes(900));
    assertEquals("1.1 KB", getReadableBytes(1100));
    assertEquals("5.7 KB", getReadableBytes(5832));
    assertEquals("1.0 MB", getReadableBytes(1024 * 1000));
    assertEquals("3.0 GB", getReadableBytes(1024 * 1024 * 1024 * 3L));
  }

  public static String getReadableBytes(long bytes){
    return getReadableBytes(bytes, 1);
  }

  public static String getReadableBytes(long bytes, int decimal){
    String[] type = {"B", "KB", "MB", "GB", "TB"};
    if(bytes < 1000) return bytes + " " + type[0];
    double val = bytes / 1024d;
    int i;
    for(i = 1; i < type.length; i++){
      if(val < 1000) break;
      val /= 1024;
    }
    double d = Math.pow(10, decimal);
    return Math.round(val * d) / d + " " + type[i];
  }


  private ByteTestUtils(){

  }
}
