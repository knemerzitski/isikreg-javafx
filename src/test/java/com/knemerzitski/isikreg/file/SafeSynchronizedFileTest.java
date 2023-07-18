package com.knemerzitski.isikreg.file;

import com.google.common.jimfs.Jimfs;
import com.knemerzitski.isikreg.extensions.TaskExecutorTestExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SafeSynchronizedFileTest extends TaskExecutorTestExtension {

  private Path path;

  private SafeSynchronizedFile<Set<String>, String> syncFile;

  private Set<String> data;

  private AtomicInteger itemWriteCounter;

  @BeforeEach
  public void setupThis() throws IOException {
    super.setupThis();
    FileSystem fileSystem = Jimfs.newFileSystem();
    path = fileSystem.getPath("sync_file.obj");

    data = new HashSet<>();

    itemWriteCounter = new AtomicInteger();
    syncFile = new SafeSynchronizedFile<Set<String>, String>(path, getTaskExecutor()) {

      @Override
      protected boolean read(InputStream inputStream, String name) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(inputStream)) {
          try {
            //noinspection unchecked
            data = (Set<String>) ois.readObject();
          } catch (ClassNotFoundException e) {
            return false;
          }
        }
        return true;
      }

      @Override
      protected boolean write(OutputStream outputStream, String name) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
          oos.writeObject(data);
          return true;
        }
      }

      @Override
      protected Set<String> startWriting(InputStream inputStream, String name) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(inputStream)) {
          try {
            //noinspection unchecked
            return (Set<String>) ois.readObject();
          } catch (ClassNotFoundException e) {
            return null;
          }
        }
      }

      @Override
      protected void write(Set<String> container, String item) throws IOException {
        itemWriteCounter.incrementAndGet();
        container.add(item);
      }

      @Override
      protected void delete(Set<String> container, String item) throws IOException {
        container.remove(item);
      }

      @Override
      protected boolean endWriting(OutputStream outputStream, String name, Set<String> container) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
          oos.writeObject(container);
          return true;
        }
      }
    };
    syncFile.setSaveCompressedZip(false);
    syncFile.setSaveDelay(10);
  }

  @Test
  public void testWriteReadDelay() throws IOException, ClassNotFoundException, TimeoutException, InterruptedException {
    add("hello");
    add("hello2");
    waitForAssertWritten(data);

    Set<String> writtenData = new HashSet<>(data);

    add("third");
    assertWritten(writtenData);
    waitForAssertWritten(data);

    writtenData = new HashSet<>(data);

    add("yes");
    add("opka");
    Thread.sleep(5);
    add("nope");
    Thread.sleep(5);
    remove("hello");
    Thread.sleep(5);
    remove("hello2");
    assertWritten(writtenData);
    waitForAssertWritten(data);
  }

  @Test
  public void testAddTwiceWriteTwice() throws IOException, ClassNotFoundException, TimeoutException, InterruptedException {
    add("1");
    waitForAssertWritten(data);
    add("2");
    waitForAssertWritten(data);
    assertEquals(1, itemWriteCounter.get());
    add("3");
    waitForAssertWritten(data);
    assertEquals(2, itemWriteCounter.get());
    add("4");
    add("4");
    add("4");
    add("5");
    waitForAssertWritten(data);
    assertEquals(6, itemWriteCounter.get());
  }

  @Test
  public void testOperationsOrdered() throws IOException, ClassNotFoundException, TimeoutException, InterruptedException {
    add("1");
    waitForAssertWritten(data);

    add("2");
    remove("2");
    add("2");
    waitForAssertWritten(data);
  }


  private void add(String value) {
    data.add(value);
    syncFile.writeItem(value);
  }

  private void remove(String value) {
    data.remove(value);
    syncFile.deleteItem(value);
  }

  private void waitForAssertWritten(Set<String> values) throws IOException, ClassNotFoundException, InterruptedException, TimeoutException {
    assertEquals(values, waitForReadPathContent());
  }

  private void assertWritten(Set<String> values) throws IOException, ClassNotFoundException, InterruptedException, TimeoutException {
    assertEquals(values, readPathContent());
  }

  private Set<String> waitForReadPathContent() throws IOException, ClassNotFoundException, TimeoutException, InterruptedException {
    syncFile.waitForWritingFinished();
    return readPathContent();
  }

  private Set<String> readPathContent() throws IOException, ClassNotFoundException, TimeoutException, InterruptedException {
    ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(syncFile.getPath()));
    //noinspection unchecked
    return (Set<String>) ois.readObject();
  }

}
