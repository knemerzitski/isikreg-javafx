package com.knemerzitski.isikreg.utils;

import java.util.HashMap;
import java.util.Map;

public class TimeUtils {

  private static final Map<String, Long> log = new HashMap<>();

  private static long time;

  public static void start() {
    time = System.nanoTime();
  }

  public static long elapsed() {
    long t = System.nanoTime();
    long r = t - time;
    time = t;
    return r;
  }

  public static double elapsedMillis() {
    long t = System.nanoTime();
    long r = t - time;
    time = t;
    return r / 1000000d;
  }

  public static void reset() {
    log.clear();
  }


  public static void measure(String key, Runnable r) {
    start();
    r.run();
    elapsedMillis(key);
  }

  private static void elapsedMillis(String key) {
    long t = System.nanoTime();
    long r = t - time;
    time = t;
    long logTime = log.getOrDefault(key, 0L);
    long totalTime = logTime + r;
    log.put(key, totalTime);
    System.out.println(key + ": " + totalTime / 1000000d + "ms");
  }

}
