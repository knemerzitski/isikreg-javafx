package com.knemerzitski.isikreg.person;

import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.exception.AppInfoException;
import com.knemerzitski.isikreg.gson.GsonBooleanProperty;
import com.knemerzitski.isikreg.gson.GsonDateProperty;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.settings.columns.TypeGroupedColumn;
import com.knemerzitski.isikreg.threading.Await;
import com.knemerzitski.isikreg.threading.TaskExecutor;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PersonListExcelWriter {

  private final Settings settings;
  private final TaskExecutor taskExecutor;

  private final Await writingAwait = new Await();

  public PersonListExcelWriter(Settings settings, TaskExecutor taskExecutor) {
    this.settings = settings;
    this.taskExecutor = taskExecutor;
  }

  public void waitForWritingFinished() throws InterruptedException {
    writingAwait.await();
  }

  public void waitForWritingFinished(long time, TimeUnit unit) throws InterruptedException {
    writingAwait.await(time, unit);
  }

  public void writeAsync(Path path, List<Person> personList, boolean groupByRegistrationType) {
    writeAsync(path, personList, groupByRegistrationType, new ProgressListener() {
      @Override
      public void start() {}

      @Override
      public void progress(double percent) {}

      @Override
      public void stop() {}
    });
  }

  public void writeAsync(Path path, List<Person> personList, boolean groupByRegistrationType, ProgressListener progressListener) {
    Callable<Boolean> task = () -> {
      try{
        progressListener.start();
        return write(path, personList, groupByRegistrationType, progressListener);
      }finally {
        writingAwait.setAwaiting(false);
        System.gc();
        progressListener.stop();
      }
    };
    writingAwait.setAwaiting(true);
    taskExecutor.submit(task);
  }

  private boolean write(Path path, List<Person> personList, boolean groupByRegistrationType, ProgressListener progressListener) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      CreationHelper createHelper = workbook.getCreationHelper();

      CellStyle dateTimeStyle = workbook.createCellStyle();
      dateTimeStyle.setDataFormat(createHelper.createDataFormat().getFormat(settings.excel.exportDateTimeFormat));

      CellStyle dateStyle = workbook.createCellStyle();
      dateStyle.setDataFormat(createHelper.createDataFormat().getFormat(settings.excel.exportDateFormat));

      XSSFSheet sheet = workbook.createSheet(settings.excel.sheetName);

      int rowIndex = 0;
      Row headerRow = sheet.createRow(rowIndex++);
      // Don't save registered column, can be seen from date column also ignore columns without label
      List<Column> columns = settings.columns.stream()
          .filter(c -> c.hasLabel() && !(c.group == Column.Group.REGISTRATION && c.id == Column.Id.REGISTERED))
          .collect(Collectors.toList());

      // GROUP BY REGISTRATION TYPE
      Column regDateColumn = columns.stream()
          .filter(c -> c.group == Column.Group.REGISTRATION && c.id == Column.Id.REGISTER_DATE)
          .findFirst().orElse(null);
      Column regTypeColumn = columns.stream()
          .filter(c -> c.group == Column.Group.REGISTRATION && c.id == Column.Id.REGISTRATION_TYPE)
          .findFirst().orElse(null);
      List<TypeGroupedColumn> groupedColumns = groupByRegistrationType ?
          settings.registrationTypeGroupColumns() : null;
      if (groupedColumns != null) {
        groupedColumns.forEach(c -> {
          int index = columns.indexOf(c.source);
          if (index != -1) {
            columns.add(index, c);
          } else {
            columns.add(c);
          }
        });
        columns.remove(regTypeColumn);
        groupedColumns.forEach(c -> columns.remove(c.source));
      }

      for (int i = 0; i < columns.size(); i++) {
        Column column = columns.get(i);
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(column.getLabel());
      }

      long lastRowIndex = headerRow.getRowNum() + personList.stream().mapToLong(p -> p.getRegistrations().size()).sum();

      Comparator<Registration> dateComparator = (o1, o2) -> {
        Date d1 = o1.getRegisteredDate();
        Date d2 = o2.getRegisteredDate();
        if (d1 != null && d2 != null) {
          return d1.compareTo(d2);
        } else if (d1 != null) {
          return 1;
        } else if (d2 != null) {
          return -1;
        }
        return 0;
      };

      List<String> registrationTypes = settings.getRegistrationTypes();

      for (Person person : personList) {
        // Sort registrations by date
        List<Registration> registrations = person.getRegistrations()
            .stream().sorted(dateComparator).collect(Collectors.toList());

        // Group registrations by type (put them on same row) if enabled in settings
        List<List<Registration>> groupedRegistrations = new ArrayList<>();
        if (groupedColumns != null && !groupedColumns.isEmpty()) {
          int counter = 0;
          while (counter < registrations.size()) {
            List<Registration> sameRowRegistrations = new ArrayList<>();
            for (TypeGroupedColumn groupedColumn : groupedColumns) {
              if (counter >= registrations.size())
                break;
              if (groupedColumn.source != regDateColumn)
                continue;
              Registration r = registrations.get(counter);
              if (r.getRegistrationType().equals(groupedColumn.type)) {
                sameRowRegistrations.add(r);
                counter++;
              } else if (!registrationTypes.contains(r.getRegistrationType())) { // Invalid registration type
                counter++;
              }
            }
            if (!sameRowRegistrations.isEmpty()) {
              groupedRegistrations.add(sameRowRegistrations);
            }
          }
        } else {
          groupedRegistrations.addAll(registrations.stream().map(Collections::singletonList).collect(Collectors.toList()));
        }

        for (List<Registration> registrationGroup : groupedRegistrations) {
          Row row = sheet.createRow(rowIndex++);
          progressListener.progress((double) (rowIndex) / lastRowIndex);

          // Write all person columns
          Map<Column, Property<?>> personProps = person.getProperties();
          personProps.forEach((column, prop) -> {
            if (!columns.contains(column))
              return;
            int k = columns.indexOf(column);
            Cell cell = row.createCell(k);
            writePropertyToCell(prop, cell, dateTimeStyle, dateStyle);
          });

          // Write all registration columns
          for (Registration registration : registrationGroup) {
            Map<Column, Property<?>> regProps = registration.getProperties();
            columns.forEach(column -> {
              Property<?> prop = null;
              if (column instanceof TypeGroupedColumn) {
                TypeGroupedColumn groupedColumn = (TypeGroupedColumn) column;
                if (registration.getRegistrationType().equals(groupedColumn.type)) {
                  prop = regProps.get(groupedColumn.source);
                }
              } else {
                prop = regProps.get(column);
              }
              if (prop != null) {
                int k = columns.indexOf(column);
                Cell cell = row.createCell(k);
                writePropertyToCell(prop, cell, dateTimeStyle, dateStyle);
              }
            });
          }
        }
      }

      progressListener.progress(-1);

      if (settings.excel.exportAutoSizeColumns) {
        for (int i = 0; i < columns.size(); i++) {
          sheet.autoSizeColumn(i);
        }
      }

      try (OutputStream out = Files.newOutputStream(path)) {
        workbook.write(out);
      } catch (FileNotFoundException | FileSystemException e) {
        throw new AppInfoException(e);
      }
      return true;
    }
  }

  private void writePropertyToCell(Property<?> prop, Cell cell, CellStyle dateTimeStyle, CellStyle dateStyle) {
    if (prop.getValue() == null)
      return;
    if (prop instanceof GsonDateProperty) {
      GsonDateProperty dateProp = (GsonDateProperty) prop;
      Date date = dateProp.get();
      if (date != null) {
        java.util.Date javaDate = java.util.Date.from(date.toInstant());
        if (date.hasTime()) {
          cell.setCellStyle(dateTimeStyle);
          cell.setCellValue(javaDate);
        } else {
          cell.setCellStyle(dateStyle);
          cell.setCellValue((int) DateUtil.getExcelDate(javaDate));
        }
      }
    } else if (prop instanceof StringProperty) {
      StringProperty stringProp = (StringProperty) prop;
//      if (NumberUtils.isCreatable(stringProp.get())) {
//        try {
//          cell.setCellValue(NumberUtils.createDouble(stringProp.get()));
//        } catch (NumberFormatException e) {
//          cell.setCellValue(stringProp.get());
//        }
//      } else {
      cell.setCellValue(stringProp.get());
//      }
    } else if (prop instanceof BooleanProperty) {
      BooleanProperty boolProp = (BooleanProperty) prop;
      cell.setCellValue(boolProp.get());
    } else if (prop instanceof GsonBooleanProperty) {
      GsonBooleanProperty boolProp = (GsonBooleanProperty) prop;
      cell.setCellValue(boolProp.get());
    }
  }


}
