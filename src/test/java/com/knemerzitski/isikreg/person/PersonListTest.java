package com.knemerzitski.isikreg.person;

import com.google.common.jimfs.Jimfs;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.threading.TaskExecutor;
import javafx.collections.ObservableMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class PersonListTest {

  private FileSystem fileSystem;
  private Settings settings;
  private TaskExecutor taskExecutor;
  private ConcurrentLinkedQueue<Throwable> uncaughtExceptions;

  private PersonList personList;

  @BeforeEach
  public void setupThis() throws IOException {
    fileSystem = Jimfs.newFileSystem();
    settings = Settings.newDefault(fileSystem.getPath("./settings.json"));

    settings.general.saveDelay = 1;

    uncaughtExceptions = new ConcurrentLinkedQueue<>();
    Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (t, e) -> {
      uncaughtExceptions.offer(e);
    };

    taskExecutor = new TaskExecutor(uncaughtExceptionHandler);

    personList = new PersonList(settings, fileSystem.getPath(settings.general.savePath), taskExecutor);
    assertNoExceptions();
  }

  @AfterEach
  public void assertNoExceptions() {
    Throwable t;
    while ((t = uncaughtExceptions.poll()) != null) {
      fail(t);
    }
  }

  @Test
  public void testWrite() throws IOException, InterruptedException {
    Person p = new Person(settings);
    p.setPersonalCode("1");
    personList.add(p);
    p.setLastName("hi");
    p.setLastName("ok");
    assertTrue(personList.verifyWritten());
  }

  @Test
  public void testEmptyPersonalCodeNotAllowed() throws IOException, InterruptedException {
    ObservableMap<String, Person> map = personList.getPersonMap();
    Person p1 = new Person(settings);
    p1.setPersonalCode("1");
    assertMapCorrect();
    personList.add(p1);
    p1.setPersonalCode("");
    assertEquals("1", p1.getPersonalCode());
    assertMapCorrect();

    Person p2 = new Person(settings);
    p2.setPersonalCode("2");
    personList.add(p2);
    p2.setPersonalCode("1");
    assertEquals(Stream.of("1", "2").collect(Collectors.toSet()), map.keySet());
    assertEquals("2", p2.getPersonalCode());
    assertMapCorrect();

    p2.setPersonalCode(null);
    assertEquals(Stream.of("1", "2").collect(Collectors.toSet()), map.keySet());
    assertEquals("2", p2.getPersonalCode());
    assertMapCorrect();

    p2.setPersonalCode("3");
    assertEquals(Stream.of("1", "3").collect(Collectors.toSet()), map.keySet());
    assertEquals("3", p2.getPersonalCode());
    assertMapCorrect();

    p1.setPersonalCode("5");
    p2.setPersonalCode("5");
    assertEquals(Stream.of("5", "3").collect(Collectors.toSet()), map.keySet());
    assertMapCorrect();

    assertTrue(personList.verifyWritten());
  }

  private void assertMapCorrect(){
    personList.getPersonMap().forEach((key, value) -> {
      assertNotNull(key);
      assertNotEquals("", key);
      assertEquals(value.getPersonalCode(), key);
    });
  }

}
