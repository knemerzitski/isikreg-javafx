package com.knemerzitski.isikreg.file;

import com.knemerzitski.isikreg.exception.AppQuitException;
import com.knemerzitski.isikreg.threading.Await;
import com.knemerzitski.isikreg.threading.TaskExecutor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public abstract class SafeSynchronizedFile<C, I> {

  class Item {
    final I item;
    final Action action;

    public Item(I item, Action action) {
      this.item = item;
      this.action = action;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Item item1 = (Item) o;
      return item.equals(item1.item) && action == item1.action;
    }

    @Override
    public int hashCode() {
      return Objects.hash(item, action);
    }

    @Override
    public String toString() {
      return item + " (" + action + ")";
    }
  }

  protected enum Action {
    WRITE, DELETE
  }

  protected interface IOFunction<I, O> {
    O run(I input) throws IOException;
  }

  private static final String EXT = "";

  public static final String EXT_ZIP = ".zip";

  private static final String EXT_BAK = ".bak";

  private static <T> T readZip(Path path, String entryName, IOFunction<InputStream, T> function) throws IOException {
    try (InputStream is = Files.newInputStream(path);
         ZipInputStream zis = new ZipInputStream(is)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().equals(entryName)) {
          return function.run(zis);
        }
        zis.closeEntry();
      }
      throw new FileNotFoundException("Zip entry not found: " + path.getFileName());
    }
  }

  private static <T> T writeZip(Path path, String entryName, IOFunction<OutputStream, T> function) throws IOException {
    try (OutputStream os = Files.newOutputStream(path);
         ZipOutputStream zos = new ZipOutputStream(os)) {
      zos.putNextEntry(new ZipEntry(entryName));
      return function.run(zos);
    }
  }


  private final TaskExecutor taskExecutor;

  private final BlockingQueue<Item> queue = new LinkedBlockingDeque<>();

  private ScheduledFuture<?> saveDelayTask;

  private final Path path;

  private final Path pathBak;
  private final Path pathZip;
  private final Path pathZipBak;
  private final Path pathFolder;

  private Runnable onStartReading;
  private Runnable onStopReading;

  // Settings
  private long saveDelay = 100; // milliseconds >= 0
  private boolean saveCompressedZip = true;

  private final Await writingScheduledAwait = new Await();

  public SafeSynchronizedFile(Path namePath, TaskExecutor taskExecutor) throws IOException {
    this.taskExecutor = taskExecutor;
    path = namePath.resolveSibling(namePath.getFileName() + EXT).toAbsolutePath();
    pathFolder = path.getParent();
    if (!Files.exists(pathFolder)) {
      throw new FileNotFoundException("Path '" + pathFolder + "' doesn't exist!");
    }

    pathBak = namePath.resolveSibling(namePath.getFileName() + EXT + EXT_BAK).toAbsolutePath();
    pathZip = namePath.resolveSibling(namePath.getFileName() + EXT_ZIP + EXT).toAbsolutePath();
    pathZipBak = namePath.resolveSibling(namePath.getFileName() + EXT_ZIP + EXT + EXT_BAK).toAbsolutePath();
  }

  public boolean read(Runnable onStartReading, Runnable onStopReading) throws IOException {
    this.onStartReading = onStartReading;
    this.onStopReading = onStopReading;
    boolean reading = read();
    if (!reading) {
      this.onStartReading = null;
      this.onStopReading = null;
    }
    return reading;
  }

  public boolean read() throws IOException {
    if (saveCompressedZip) {
      return readZip();
    }
    if (Files.exists(path)) {
      if (Files.exists(pathBak))
        Files.delete(pathBak);
      readAsync();
      return true;
    } else if (Files.exists(pathBak)) {
      Files.move(pathBak, path);
      readAsync();
      return true;
    }
    if (!saveCompressedZip)
      return readZip();
    return false;
  }

  private boolean readZip() throws IOException {
    if (Files.exists(pathZip)) {
      if (Files.exists(pathZipBak))
        Files.delete(pathZipBak);
      readZipAsync();
      return true;
    } else if (Files.exists(pathZipBak)) {
      Files.move(pathZipBak, pathZip);
      readZipAsync();
      return true;
    }
    return false;
  }


  protected <T> T readIn(IOFunction<InputStream, T> callback) throws IOException {
    if (saveCompressedZip) {
      return readZip(this.pathZip, this.path.getFileName().toString(), callback);
    } else {
      try (InputStream is = Files.newInputStream(this.path)) {
        return callback.run(is);
      }
    }
  }

  private void runStartReading() {
    if (onStartReading == null) return;
    onStartReading.run();
    onStartReading = null;
  }

  private void runStopReading() {
    if (onStopReading == null) return;
    onStopReading.run();
    onStopReading = null;
  }

  private void readAsync() {
    Path workingPath = this.path;
    taskExecutor.submit(() -> {
      try {
        runStartReading();
        try (InputStream is = Files.newInputStream(workingPath)) {
          return read(is, workingPath.getFileName().toString());
        }
      }finally {
        System.gc();
        runStopReading();
      }
    });
  }

  private void readZipAsync() {
    Path workingPath = this.pathZip;
    String name = this.path.getFileName().toString();
    taskExecutor.submit(() -> {
      try {
        runStartReading();
        return readZip(workingPath, name, (is) -> read(is, name));
      }finally {
        System.gc();
        runStopReading();
      }
    });
  }

  protected abstract boolean read(InputStream inputStream, String name) throws IOException;

  public void waitForWritingFinished() throws InterruptedException {
    writingScheduledAwait.await();
  }

  protected synchronized void offerItem(I item, Action action) {
    queue.offer(new Item(item, action));
    if (saveDelayTask != null) {
      saveDelayTask.cancel(true);
    }

    writingScheduledAwait.setAwaiting(true);

    try {
      saveDelayTask = taskExecutor.schedule(() -> {
        try {
          processQueue();
        } finally {
          writingScheduledAwait.setAwaiting(false);
        }
        return true;
      }, saveDelay, TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      writingScheduledAwait.setAwaiting(false);
      if (taskExecutor.isStopping()) {
        throw new AppQuitException("Failed to save latest changes. Application is quitting...");
      } else {
        throw e;
      }
    }
  }

  protected void writeItem(I item) {
    offerItem(item, Action.WRITE);
  }

  protected void deleteItem(I item) {
    offerItem(item, Action.DELETE);
  }

  private boolean write() throws IOException {
    if (saveCompressedZip) {
      writeZip(pathZip, path.getFileName().toString(), (os) -> write(os, path.getFileName().toString()));
      if (Files.exists(path))
        Files.delete(path);
    } else {
      try (OutputStream os = Files.newOutputStream(path)) {
        write(os, path.getFileName().toString());
      }
      if (Files.exists(pathZip))
        Files.delete(pathZip);
    }
    return true;
  }

  protected boolean exists() {
    return Files.exists(getPath());
  }

  private synchronized void processQueue() throws IOException {
    if (queue.isEmpty())
      return;

    if (!exists()) {
      write();
      queue.clear();
    } else {
      C container;
      if (saveCompressedZip) {
        container = readZip(pathZip, path.getFileName().toString(), (is) -> startWriting(is, path.getFileName().toString()));
      } else {
        try (InputStream is = Files.newInputStream(path)) {
          container = startWriting(is, path.getFileName().toString());
        }
      }

      Item item;
      while ((item = queue.poll()) != null) {
        processItem(container, item);
      }
      if (saveCompressedZip) {
        writeZip(pathZipBak, path.getFileName().toString(), (os) -> endWriting(os, path.getFileName().toString(), container));
        Files.delete(pathZip);
        Files.move(pathZipBak, pathZip);
        if (Files.exists(path))
          Files.delete(path);
      } else {
        try (OutputStream os = Files.newOutputStream(pathBak)) {
          endWriting(os, path.getFileName().toString(), container);
        }
        Files.delete(path);
        Files.move(pathBak, path);
        if (Files.exists(pathZip))
          Files.delete(pathZip);
      }

    }
  }

  private void processItem(C container, Item item) throws IOException {
    switch (item.action) {
      case WRITE:
        write(container, item.item);
        break;
      case DELETE:
        delete(container, item.item);
        break;
    }
  }

  public void writeAsync() {
    taskExecutor.submit(this::write);
  }

  public Path getPath() {
    return saveCompressedZip ? pathZip : path;
  }


  protected abstract boolean write(OutputStream outputStream, String name) throws IOException;

  protected abstract C startWriting(InputStream inputStream, String name) throws IOException;

  protected abstract void write(C container, I item) throws IOException;

  protected abstract void delete(C container, I item) throws IOException;

  protected abstract boolean endWriting(OutputStream outputStream, String name, C container) throws IOException;

  protected void delete() throws IOException {
    if (Files.exists(path))
      Files.delete(path);
    if (Files.exists(pathBak))
      Files.delete(pathBak);
    if (Files.exists(pathZip))
      Files.delete(pathZip);
    if (Files.exists(pathZipBak))
      Files.delete(pathZipBak);
  }


  protected void setSaveDelay(long saveDelay) {
    this.saveDelay = saveDelay;
  }

  protected void setSaveCompressedZip(boolean saveCompressedZip) {
    this.saveCompressedZip = saveCompressedZip;
  }

}
