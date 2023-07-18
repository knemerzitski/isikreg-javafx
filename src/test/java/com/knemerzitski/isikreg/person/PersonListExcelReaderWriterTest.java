package com.knemerzitski.isikreg.person;

import com.google.common.jimfs.Jimfs;
import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.threading.TaskExecutor;
import com.knemerzitski.isikreg.utils.ExcelUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class PersonListExcelReaderWriterTest {

  private FileSystem fileSystem;
  private Settings settings;
  private TaskExecutor taskExecutor;
  private ConcurrentLinkedQueue<Throwable> uncaughtExceptions;

  @BeforeEach
  public void setupThis() {
    fileSystem = Jimfs.newFileSystem();
    settings = Settings.newDefault(fileSystem.getPath("./settings.json"));

    settings.general.saveDelay = 1;
    settings.general.saveCompressedZip = false;

    uncaughtExceptions = new ConcurrentLinkedQueue<>();
    Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (t, e) -> {
      uncaughtExceptions.offer(e);
    };

    taskExecutor = new TaskExecutor(uncaughtExceptionHandler);

    assertNoExceptions();
  }

  @AfterEach
  public void assertNoExceptions() {
    Throwable t;
    while ((t = uncaughtExceptions.poll()) != null) {
      fail(t);
    }
  }

  // ##################################### Tests ####################################

  @Test
  public void testOne() throws InterruptedException, IOException {
    List<Person> personList = new ArrayList<>();
    Person p = new Person(settings);
    p.setPersonalCode("1");
    p.setLastName("ok");
    Registration r = p.getOrNewNextRegistration();
    r.setRegistered(true, true);
    r.setRegisteredDate(new Date(LocalDate.of(2000, 10, 15)));
    personList.add(p);

    String[][] expected = {
        {"Registreerimise tüüp", "Registreerimise aeg", "Isikukood", "Perekonnanimi", "Eesnimi"},
        {"Sisse", r.getRegisteredDate().toString(), "1", "ok", null}
    };
    assertExcelWriter(expected, personList, false);
    assertExcelReader(personList);
  }

  @Test
  public void testNullDate() throws InterruptedException, IOException {
    List<Person> personList = new ArrayList<>();
    Person p = new Person(settings);
    p.setPersonalCode("1");
    p.setLastName("ok");
    Registration r = p.getOrNewRegistration();
    r.setRegistrationType("Välja");
    personList.add(p);

    String[][] expected = {
        {"Registreerimise tüüp", "Registreerimise aeg", "Isikukood", "Perekonnanimi", "Eesnimi"},
        {"Välja", null, "1", "ok", null}
    };
    assertExcelWriter(expected, personList, false);
    assertExcelReader(personList);
  }

  @Test
  public void testMany() throws InterruptedException, IOException {
    List<Person> personList = new ArrayList<>();

    Random rand = new Random(54336362);

    List<List<String>> expected = new ArrayList<>();
    expected.add(Stream.of("Registreerimise tüüp", "Registreerimise aeg", "Isikukood", "Perekonnanimi", "Eesnimi").collect(Collectors.toList()));
    for (int i = 0; i < 500; i++) {
      Person p = new Person(settings);
      p.setPersonalCode("c" + i);
      p.setLastName("last" + i);
      double chance = 0.95;
      while (rand.nextDouble() < chance) {
        Registration r;
        r = p.getOrNewRegistration(settings.getRegistrationTypes().get(rand.nextInt(settings.getRegistrationTypes().size())));
        r.setRegistered(true, true);
        r.setRegisteredDate(new Date(LocalDateTime.of(
            LocalDate.of(2000 + rand.nextInt(15), 1 + rand.nextInt(12), 1 + rand.nextInt(27)),
            LocalTime.of(1 + rand.nextInt(23), 1 + rand.nextInt(59), 1 + rand.nextInt(59))
        )));
        chance *= 0.75;
      }
      p.getRegistrations().stream().sorted((o1, o2) -> o1.getRegisteredDate() != null ? o1.getRegisteredDate().compareTo(o2.getRegisteredDate()) : 0).forEach(r -> {
        expected.add(Stream.of(
            r.getRegistrationType(),
            r.getRegisteredDate() != null ? r.getRegisteredDate().toString() : null,
            p.getPersonalCode(),
            !p.getLastName().isEmpty() ? p.getLastName() : null,
            !p.getFirstName().isEmpty() ? p.getFirstName() : null
        ).collect(Collectors.toList()));
      });
      personList.add(p);
    }

    assertExcelWriter(expected, personList, false);
    assertExcelReader(personList);
  }

  @Test
  public void testOneGrouped() throws InterruptedException, IOException {
    List<Person> personList = new ArrayList<>();
    Person p = new Person(settings);
    p.setPersonalCode("1");
    p.setLastName("ok");
    Registration r = p.getOrNewNextRegistration();
    r.setRegistered(true, true);
    r.setRegisteredDate(new Date(LocalDate.of(2000, 10, 15)));
    personList.add(p);

    String[][] expected = {
        {"Sisse registreerimise aeg", "Välja registreerimise aeg", "Isikukood", "Perekonnanimi", "Eesnimi"},
        {r.getRegisteredDate().toString(), null, "1", "ok", null}
    };
    assertExcelWriter(expected, personList, true);
    assertExcelReader(personList);
  }

  @Test
  public void testGroupedCorrectDateOrdering() throws InterruptedException, IOException {
    List<Person> personList = new ArrayList<>();

    Person p = new Person(settings);
    personList.add(p);
    p.setPersonalCode("1");
    p.setLastName("ok");

    Registration r0 = p.getOrNewRegistration("Välja");
    r0.setRegisteredNoConfirm(new Date(LocalDate.of(2000, 10, 15)));

    Registration r1 = p.getOrNewRegistration("Sisse");
    r1.setRegisteredNoConfirm(new Date(LocalDate.of(2000, 11, 16)));
    Registration r2 = p.getOrNewRegistration("Välja");
    r2.setRegisteredNoConfirm(new Date(LocalDate.of(2000, 11, 18)));

    Registration r3 = p.getOrNewRegistration("Välja");
    r3.setRegisteredNoConfirm(new Date(LocalDate.of(2000, 12, 19)));

    Registration r4 = p.getOrNewRegistration("Välja");
    r4.setRegisteredNoConfirm(new Date(LocalDate.of(2000, 10, 10)));
    Registration r5 = p.getOrNewRegistration("Sisse");
    r5.setRegisteredNoConfirm(new Date(LocalDate.of(2000, 10, 9)));

    Registration r6 = p.getOrNewRegistration("Sisse");
    r6.setRegisteredNoConfirm(new Date(LocalDate.of(2000, 11, 17)));

    String[][] expected = {
        {"Sisse registreerimise aeg", "Välja registreerimise aeg", "Isikukood", "Perekonnanimi", "Eesnimi"},
        {r5.getRegisteredDate().toString(), r4.getRegisteredDate().toString(), "1", "ok", null},
        {null, r0.getRegisteredDate().toString(), "1", "ok", null},
        {r1.getRegisteredDate().toString(), null, "1", "ok", null},
        {r6.getRegisteredDate().toString(), r2.getRegisteredDate().toString(), "1", "ok", null},
        {null, r3.getRegisteredDate().toString(), "1", "ok", null},
    };
    assertExcelWriter(expected, personList, true);
    assertExcelReader(personList);
  }


  // ##################################### Helper methods ############################

  private void assertExcelWriter(String[][] expected, List<Person> personList, boolean grouped) throws InterruptedException, IOException {
    assertExcelWriter(arrayToList(expected), personList, grouped);
  }

  private void assertExcelWriter(List<List<String>> expected, List<Person> personList, boolean grouped) throws InterruptedException, IOException {
    PersonListExcelWriter writer = new PersonListExcelWriter(settings, taskExecutor);

    Path path = fileSystem.getPath("test.xlsx");
    writer.writeAsync(path, personList, grouped);
    writer.waitForWritingFinished(5, TimeUnit.SECONDS);

    assertTablesEqual(expected, readExcelToString(path));
  }

  private void assertExcelReader(List<Person> expected) throws InterruptedException {
    List<Person> personList = new ArrayList<>();
    PersonListExcelReader reader = new PersonListExcelReader(settings, taskExecutor) {
      @Override
      protected void process(Person person) {
        personList.add(person);
      }
    };

    Path path = fileSystem.getPath("test.xlsx");

    reader.readAsync(Stream.of(path).collect(Collectors.toList()));
    reader.waitForReadingFinished(5, TimeUnit.SECONDS);

    assertPeopleListsEqual(expected, personList);
  }

  public boolean assertPeopleListsEqual(List<Person> list1, List<Person> list2) {
    if (list1 == null) return list2 == null;
    if(list1.size() != list2.size()) return false;

    List<Person> remaining = new ArrayList<>(list1);
    for (Person p : list1) {
      boolean found = false;
      Iterator<Person> itr = remaining.iterator();
      while (itr.hasNext()) {
        Person p2 = itr.next();
        if (p.equals(p2)) {
          itr.remove();
          found = true;
          break;
        }
      }
      if (!found) return false;
    }
    return true;
  }

  private void assertTablesEqual(List<List<String>> a, List<List<String>> b) {
    assertEquals(a.size(), b.size(), "Rows count mismatch");
    for (int i = 0; i < a.size(); i++) {
      List<String> r1 = a.get(i);
      List<String> r2 = b.get(i);
      assertEquals(r1.size(), r2.size(), String.format("Row cells count mismatch at row %s. %s <==> %s", i, r1, r2));
      for (int j = 0; j < r1.size(); j++) {
        String cell1 = r1.get(j);
        String cell2 = r2.get(j);
        assertEquals(cell1, cell2, String.format("Cells don't match at (%s,%s)", i, j));
      }
    }
  }

  private List<List<String>> readExcelToString(Path path) throws IOException {
    try (InputStream is = Files.newInputStream(path)) {
      try (XSSFWorkbook workbook = new XSSFWorkbook(is)) {
        is.close();

        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        XSSFSheet sheet = workbook.getSheet(settings.excel.sheetName);
        if (sheet == null)
          sheet = workbook.getSheetAt(0);

        List<List<String>> excel = new ArrayList<>();
        int start = 0, end = 0;
        for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
          Row row = sheet.getRow(i);
          if (i == 0) {
            start = row.getFirstCellNum();
            end = row.getLastCellNum();
          }
          List<String> rowList = new ArrayList<>();
          for (int j = start; j < end; j++) {
            Cell cell = row.getCell(j);
            if (cell == null) {
              rowList.add(null);
              continue;
            }
            String value;
            if (cell.getCellTypeEnum() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
              java.util.Date javaDate = ExcelUtils.getCellDate(cell, evaluator);
              if (javaDate != null) {
                Date date;
                if (ExcelUtils.isDateTimeStyle(cell.getCellStyle())) {
                  // DateTime
                  date = new Date(javaDate.toInstant());
                } else {
                  double excelValue = DateUtil.getExcelDate(javaDate);
                  if (Math.ceil(excelValue) == Math.floor(excelValue)) {
                    // Date
                    date = new Date(LocalDate.from(javaDate.toInstant().atZone(ZoneId.systemDefault())));
                  } else {
                    // DateTime
                    date = new Date(javaDate.toInstant());
                  }
                }
                value = date.toString();
              } else {
                value = null;
              }
            } else {
              value = ExcelUtils.getCellString(cell, evaluator);
            }

            rowList.add(value);
          }
          excel.add(rowList);
        }
        return excel;
      }
    }
  }

  private String[][] listToArray(List<List<String>> list) {
    return list.stream().map(l -> l.toArray(new String[0])).collect(Collectors.toList()).toArray(new String[0][0]);
  }

  private List<List<String>> arrayToList(String[][] arr) {
    List<List<String>> list = new ArrayList<>();
    for (int i = 0; i < arr.length; i++) {
      String[] subArr = arr[i];
      List<String> subList = new ArrayList<>();
      for (int j = 0; j < subArr.length; j++) {
        subList.add(subArr[j]);
      }
      list.add(subList);
    }
    return list;
  }
}
