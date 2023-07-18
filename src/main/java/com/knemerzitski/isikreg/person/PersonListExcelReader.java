package com.knemerzitski.isikreg.person;

import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.exception.AppQuitException;
import com.knemerzitski.isikreg.gson.GsonBooleanProperty;
import com.knemerzitski.isikreg.gson.GsonDateProperty;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.settings.columns.TypeGroupedColumn;
import com.knemerzitski.isikreg.threading.Await;
import com.knemerzitski.isikreg.threading.TaskExecutor;
import com.knemerzitski.isikreg.utils.ExcelUtils;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class PersonListExcelReader {

  private final Settings settings;

  private final TaskExecutor taskExecutor;

  private final Await readingAwait = new Await();

  public PersonListExcelReader(Settings settings, TaskExecutor taskExecutor) {
    this.settings = settings;
    this.taskExecutor = taskExecutor;
  }

  public void waitForReadingFinished() throws InterruptedException {
    readingAwait.await();
  }

  public void waitForReadingFinished(long time, TimeUnit unit) throws InterruptedException {
    readingAwait.await(time, unit);
  }

  public void readAsync(List<Path> paths) {
    readAsync(paths, new ProgressListener() {
      @Override
      public void start() {
      }

      @Override
      public void progress(double percent) {
      }

      @Override
      public void stop() {
      }
    });
  }

  public void readAsync(List<Path> paths, ProgressListener progressListener) {
    System.out.println("Read excel files: " + paths);

    Callable<Boolean> task = () -> {
      try{
        progressListener.start();
        return read(paths, progressListener);
      }finally {
        readingAwait.setAwaiting(false);
        System.gc();
        progressListener.stop();
      }
    };

    readingAwait.setAwaiting(true);
    taskExecutor.submit(task);
  }

  protected abstract void process(Person person);

  private boolean read(List<Path> paths, ProgressListener progressListener) {
    // each file should get a fraction
    int fileCount = paths.size();
    DoubleProperty progress = new SimpleDoubleProperty();
    paths.parallelStream().forEach(path -> {
      IntegerProperty count = new SimpleIntegerProperty(0);
      IntegerProperty totalCount = new SimpleIntegerProperty(0);
      DoubleProperty fileProgress = new SimpleDoubleProperty(0);
      ChangeListener<Number> listener = (observable, oldValue, newValue) -> {
        if (count.get() >= 0 && totalCount.get() > 0) {
          double newFileProgress = count.doubleValue() / totalCount.doubleValue();
          synchronized (progress) {
            progress.set(1f / fileCount * (newFileProgress - fileProgress.get()) + progress.get());
            progressListener.progress(progress.get());
          }
          fileProgress.set(newFileProgress);
        }
      };
      count.addListener(listener);
      totalCount.addListener(listener);
      try {
        Collection<Person> people = parse(path, count, totalCount);
        if (people != null) {
          people.forEach(p -> {
            count.set(count.get() + 1);
            process(p);
          });
        } else {
          count.set(totalCount.get());
        }
      } catch (IOException e) {
        throw new AppQuitException(e);
      } finally {
        count.removeListener(listener);
        totalCount.removeListener(listener);
      }
    });
    return true;
  }

  private Collection<Person> parse(Path path, IntegerProperty currentCount, IntegerProperty totalCount) throws IOException {
//  private Map<String, Person> parse(File file) throws IOException {
    System.out.println("Reading " + path);

    try (InputStream is = Files.newInputStream(path)) {
      try (XSSFWorkbook workbook = new XSSFWorkbook(is)) {
        is.close();
        System.out.println("Done reading " + path);

        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        XSSFSheet sheet = workbook.getSheet(settings.excel.sheetName);
        if (sheet == null)
          sheet = workbook.getSheetAt(0);

        totalCount.set(totalCount.get() + (sheet.getLastRowNum() - sheet.getFirstRowNum()));

        // Find header, first cell that has value
        Row headerRow = null;
        int headerRowIndex, headerStartCellIndex = 0;
        for (headerRowIndex = sheet.getFirstRowNum(); headerRowIndex <= sheet.getLastRowNum(); headerRowIndex++) {
          Row row = sheet.getRow(headerRowIndex);
          for (headerStartCellIndex = row.getFirstCellNum(); headerStartCellIndex < row.getLastCellNum(); headerStartCellIndex++) {
            Cell cell = row.getCell(headerStartCellIndex);
            if (!ExcelUtils.isEmpty(cell, evaluator)) {
              headerRow = row;
              break;
            }
          }
          if (headerRow != null)
            break;
        }
        if (headerRow == null)
          return null;

        // Column by label
        Map<String, Column> labelToColumn = settings.columns.stream().filter(Column::hasLabel)
            .collect(Collectors.toMap(column -> column.getLabel().toLowerCase(), Function.identity()));

        // Add grouped columns
        List<TypeGroupedColumn> groupedColumns = settings.registrationTypeGroupColumns();
        if (groupedColumns != null) {
          groupedColumns.forEach(c -> labelToColumn.put(c.getLabel().toLowerCase(), c));
        }

        // Find columns by header name
        Map<Integer, Column> colIndexToColumn = new HashMap<>();
        for (int j = headerStartCellIndex; j < headerRow.getLastCellNum(); j++) {
          Cell cell = headerRow.getCell(j);
          if (cell == null)
            continue;
          String value = ExcelUtils.getCellString(cell, evaluator);
          if (value != null && !value.isEmpty()) {
            // determine column for index
            Column column = labelToColumn.get(value.toLowerCase());
            if (column != null) {
              colIndexToColumn.put(j, column);
            }
          }
        }

        Map<String, Person> personMap = new HashMap<>();
        Set<Map.Entry<Integer, Column>> collIndexColumnEntries = colIndexToColumn.entrySet();
        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
          Row row = sheet.getRow(i);
          if (row == null)
            continue;
          Person person = new Person(settings);
          Registration registration = person.getLatestRegistration();
          Map<Column, Property<?>> properties = registration.getWithPersonProperties();
          for (Map.Entry<Integer, Column> entry : collIndexColumnEntries) {
            Cell cell = row.getCell(entry.getKey());
            if (cell == null)
              continue;
            Column column = entry.getValue();
            Property<?> property;
            if (column instanceof TypeGroupedColumn) {
              TypeGroupedColumn groupedColumn = (TypeGroupedColumn) column;
              registration = person.getRegistrations().stream()
                  .filter(r -> r.getRegistrationType().equals(groupedColumn.type))
                  .findFirst().orElse(null);
              if (registration == null) {
                registration = person.newRegistration();
                registration.setRegistrationType(groupedColumn.type);
              }
              properties = registration.getWithPersonProperties();
              property = properties.get(groupedColumn.source);
            } else {
              property = properties.get(column);
            }
            if (property instanceof GsonDateProperty) {
              GsonDateProperty dateProperty = (GsonDateProperty) property;
              java.util.Date value = ExcelUtils.getCellDate(cell, evaluator);
              if (value != null) {
                if (ExcelUtils.isDateTimeStyle(cell.getCellStyle())) {
                  // DateTime
                  dateProperty.set(new Date(value.toInstant()));
                } else {
                  double excelValue = DateUtil.getExcelDate(value);
                  if (Math.ceil(excelValue) == Math.floor(excelValue)) {
                    // Date
                    dateProperty.set(new Date(LocalDate.from(value.toInstant().atZone(ZoneId.systemDefault()))));
                  } else {
                    // DateTime
                    dateProperty.set(new Date(value.toInstant()));
                  }
                }
              }
            } else if (property instanceof StringProperty) {
              StringProperty stringProperty = (StringProperty) property;
              String value = ExcelUtils.getCellString(cell, evaluator);
              stringProperty.set(value);
            } else if (property instanceof BooleanProperty) {
              BooleanProperty booleanProperty = (BooleanProperty) property;
              Boolean value = ExcelUtils.getCellBoolean(cell, evaluator);
              booleanProperty.set(value != null ? value : false);
            } else if (property instanceof GsonBooleanProperty) {
              GsonBooleanProperty booleanProperty = (GsonBooleanProperty) property;
              Boolean value = ExcelUtils.getCellBoolean(cell, evaluator);
              booleanProperty.set(value);
            }
          }
          if (!person.getPersonalCode().isEmpty()) {
            Person existingPerson = personMap.get(person.getPersonalCode());
            if (existingPerson != null) {
              existingPerson.merge(person);
            } else {
              personMap.put(person.getPersonalCode(), person);
              totalCount.set(totalCount.get() + 1);
            }
          }
          currentCount.set(currentCount.get() + 1);
        }
        Collection<Person> personList = personMap.values();
        personList.forEach(Person::cleanUpRegistrations);
        return personList;
      }
    }
  }

}
