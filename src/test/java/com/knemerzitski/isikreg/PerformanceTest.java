package com.knemerzitski.isikreg;

import com.google.common.jimfs.Jimfs;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.utils.ByteTestUtils;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(ApplicationExtension.class)
public class PerformanceTest {

  private IsikReg isikReg;
  private ConcurrentLinkedQueue<Throwable> uncaughtExceptions;

  @Start
  public void init(Stage primaryStage) {
    uncaughtExceptions = new ConcurrentLinkedQueue<>();

    FileSystem fileSystem = Jimfs.newFileSystem();
    Settings settings = Settings.newDefault(fileSystem.getPath("./settings.json"));

    settings.smartCard.waitBeforeReadingCard = 0; // Don't need to wait with fake terminals
    settings.smartCard.waitForChangeLoopInterval = 1; // Must be positive with fake terminals, or will stay stuck waiting
    settings.smartCard.showSuccessStatusDuration = 10;
    settings.general.saveDelay = 1;

    Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (t, e) -> {
      uncaughtExceptions.offer(e);
    };

    isikReg = new IsikReg(fileSystem, settings, uncaughtExceptionHandler);

    isikReg.start(primaryStage);
  }

  @BeforeEach
  @AfterEach
  public void assertNoExceptions() {
    Throwable t;
    while ((t = uncaughtExceptions.poll()) != null) {
      fail(t);
    }
  }

  /*
  [Performance summary] Baseline used memory: 12.5 MB
  #0:	Memory[Total: 415.8 MB, Used: 12.9 MB, Free: 402.9 MB]	File[isikreg.json.zip 0 B] One[Memory:444.7 KB File:0 B] (Avg n.10)
  #10000:	Memory[Total: 490.5 MB, Used: 44.8 MB, Free: 445.7 MB]	File[isikreg.json.zip 281.0 KB] One[Memory:3.3 KB File:28 B] (Avg n.1)
  #20000:	Memory[Total: 628.5 MB, Used: 72.1 MB, Free: 556.4 MB]	File[isikreg.json.zip 565.5 KB] One[Memory:3.1 KB File:28 B] (Avg n.1)
  #30000:	Memory[Total: 628.5 MB, Used: 101.0 MB, Free: 527.5 MB]	File[isikreg.json.zip 852.2 KB] One[Memory:3.0 KB File:29 B] (Avg n.1)
  #40000:	Memory[Total: 895.5 MB, Used: 130.6 MB, Free: 764.9 MB]	File[isikreg.json.zip 1.1 MB] One[Memory:3.0 KB File:29 B] (Avg n.1)
  #50000:	Memory[Total: 1.2 GB, Used: 160.7 MB, Free: 1.0 GB]	File[isikreg.json.zip 1.4 MB] One[Memory:3.0 KB File:29 B] (Avg n.1)
  #60000:	Memory[Total: 1.2 GB, Used: 188.6 MB, Free: 1.0 GB]	File[isikreg.json.zip 1.7 MB] One[Memory:3.0 KB File:29 B] (Avg n.1)
  #70000:	Memory[Total: 2.2 GB, Used: 222.5 MB, Free: 2.0 GB]	File[isikreg.json.zip 1.9 MB] One[Memory:3.1 KB File:29 B] (Avg n.1)
  #80000:	Memory[Total: 2.2 GB, Used: 246.8 MB, Free: 1.9 GB]	File[isikreg.json.zip 2.2 MB] One[Memory:3.0 KB File:29 B] (Avg n.1)
  #90000:	Memory[Total: 2.2 GB, Used: 276.8 MB, Free: 1.9 GB]	File[isikreg.json.zip 2.5 MB] One[Memory:3.0 KB File:29 B] (Avg n.1)
  #100000:	Memory[Total: 2.2 GB, Used: 291.7 MB, Free: 2.0 GB]	File[isikreg.json.zip 2.8 MB] One[Memory:2.9 KB File:29 B] (Avg n.1)

  Memory usage: 3*(4300 + rows) KiB
  File size: 29*rows B
  */
  @Test
  public void testMemoryUsageAndSaveFileSize(FxRobot robot) throws IOException, InterruptedException {
    Actions actions = new Actions(isikReg, null, robot, 53242342);

    PerformanceTracker perf = new PerformanceTracker();
    for(int i = 0;i<10; i++) {
      perf.add(getRegistrationsCount());
    }

    int registrationsCountLimit = 100000;
    double currentRegistrationsCount = 0;
    int steps = 10;
    int log = 10;
    for(int pow = 4; currentRegistrationsCount < registrationsCountLimit; pow++){
      double scale  = Math.pow(log, pow);
      for(int step = 1; step < steps; step++){
        currentRegistrationsCount = step*scale;
        if(currentRegistrationsCount > registrationsCountLimit) break;
        actions.populateTable(Math.max(0, (int)currentRegistrationsCount - getRegistrationsCount()));
        perf.add(getRegistrationsCount());
      }
    }
    perf.printSummary();
  }

  @Test
  public void testNoMemoryLeaks(FxRobot robot) throws IOException, InterruptedException {
    int count = 1000;
    int iterations = 30000 / count;
    double threshold = 2;

    Actions actions = new Actions(isikReg, null, robot, 4212321);

    List<Long> memList = new ArrayList<>();
    for (int k = 0; k < iterations; k++) {
      actions.populateTable(count);
      MemoryStats stats = new MemoryStats();
      isikReg.getPersonList().clear();
      memList.add(stats.totalMemory - stats.freeMemory);
      double inc = 1d * memList.get(memList.size()-1) / memList.get(0);
      assertTrue(inc < threshold, "Increasing memory: " + getBytesChange(memList).toString());
    }

    System.out.println("[Memory usages after GC]");
    System.out.println(getBytesChange(memList));
  }


  // ###################################### Helpers ###################################

  private class PerformanceTracker {

    private long baselineUsedMemory = Long.MAX_VALUE;

    private final Map<Integer, List<MemoryStats>> resultMap = new HashMap<>();

    public void add(int count) throws IOException, InterruptedException {
      MemoryStats stats = new MemoryStats();
      baselineUsedMemory = Math.min(baselineUsedMemory, stats.totalMemory - stats.freeMemory);
      resultMap.computeIfAbsent(count, k -> new ArrayList<>()).add(stats);
    }

    public void printSummary() {
      System.out.printf("[Performance summary] Baseline used memory: %s\n", ByteTestUtils.getReadableBytes(baselineUsedMemory));
      Path saveFileName = isikReg.getPersonList().getPath().getFileName();
      resultMap.keySet().stream().sorted().forEach(key -> {
        List<MemoryStats> statsList = resultMap.get(key);
        int count = Math.max(1, key);
        long avgTotalMemory = statsList.stream().mapToLong(s -> s.totalMemory / statsList.size()).sum();
        long avgFreeMemory = statsList.stream().mapToLong(s -> s.freeMemory / statsList.size()).sum();
        long avgUsedMemory = avgTotalMemory - avgFreeMemory;

        long avgSaveFileSize = statsList.stream().mapToLong(s -> s.saveFileSize / statsList.size()).sum();

        long oneMemory = (avgUsedMemory - baselineUsedMemory)/count;
        long oneFile = avgSaveFileSize/count;

        System.out.printf("#%s:\tMemory[Total: %s, Used: %s, Free: %s]\tFile[%s %s] One[Memory:%s File:%s] (Avg n.%s)\n", key,
            ByteTestUtils.getReadableBytes(avgTotalMemory), ByteTestUtils.getReadableBytes(avgUsedMemory), ByteTestUtils.getReadableBytes(avgFreeMemory),
            saveFileName, ByteTestUtils.getReadableBytes(avgSaveFileSize), ByteTestUtils.getReadableBytes(oneMemory), ByteTestUtils.getReadableBytes(oneFile), statsList.size());
      });
    }
  }

  private class MemoryStats {

    private final long totalMemory;
    private final long freeMemory;
    private final long saveFileSize;

    public MemoryStats() throws InterruptedException, IOException {
      System.gc(); System.gc();
      System.runFinalization();

      Runtime rt = Runtime.getRuntime();
      totalMemory = rt.totalMemory();
      freeMemory = rt.freeMemory();

      isikReg.getPersonList().waitForWritingFinished();
      Path path = isikReg.getPersonList().getPath();
      saveFileSize = Files.exists(path) ? Files.size(path) : 0;
    }

  }

  private int getRegistrationsCount() {
    return isikReg.getPersonList().getUnmodifiableList().stream().mapToInt(p -> p.getRegistrations().size()).sum();
  }

  private static List<String> getBytesChange(List<Long> memList){
    return memList.stream()
        .map(m -> String.format("%s (%s%%)", ByteTestUtils.getReadableBytes(m), Math.round((1.0d * m/memList.get(0))*100)))
        .collect(Collectors.toList());
  }

}
