package com.knemerzitski.isikreg.utils;

import org.apache.poi.ss.usermodel.*;

import java.util.Date;

public class ExcelUtils {

  private ExcelUtils() {
  }

  public static boolean isDateTimeStyle(CellStyle style) {
    String s = style.getDataFormatString();
    return s.contains("h") || s.contains("s");
  }

  public static boolean isEmpty(Cell cell, FormulaEvaluator fe) {
    switch (cell.getCellTypeEnum()) {
      case STRING:
        return cell.getStringCellValue().trim().isEmpty();
      case NUMERIC:
      case BOOLEAN:
        return false;
      case FORMULA:
        isEmpty(fe.evaluateInCell(cell), fe);
      default:
        return true;
    }
  }

  public static boolean isEmpty(CellValue cell) {
    switch (cell.getCellTypeEnum()) {
      case STRING:
        return cell.getStringValue().trim().isEmpty();
      case NUMERIC:
      case BOOLEAN:
        return false;
      default:
        return true;
    }
  }

  public static String getCellString(Cell cell, FormulaEvaluator fe) {
    switch (cell.getCellTypeEnum()) {
      case STRING:
        return cell.getStringCellValue().trim();
      case NUMERIC:
        if (cell.getNumericCellValue() == (long) cell.getNumericCellValue()) {
          return String.valueOf((long) cell.getNumericCellValue());
        } else {
          return String.valueOf(cell.getNumericCellValue());
        }
      case BOOLEAN:
        return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
      case FORMULA:
        return getCellString(fe.evaluate(cell));
      default:
        return null;
    }
  }

  public static String getCellString(CellValue cell) {
    switch (cell.getCellTypeEnum()) {
      case STRING:
        return cell.getStringValue().trim();
      case NUMERIC:
        if (cell.getNumberValue() == (long) cell.getNumberValue()) {
          return String.valueOf((long) cell.getNumberValue());
        } else {
          return String.valueOf(cell.getNumberValue());
        }
      case BOOLEAN:
        return cell.getBooleanValue() ? "TRUE" : "FALSE";
      default:
        return null;
    }
  }

  public static Boolean getCellBoolean(Cell cell, FormulaEvaluator fe) {
    switch (cell.getCellTypeEnum()) {
      case STRING:
        return !cell.getStringCellValue().trim().isEmpty();
      case NUMERIC:
        return cell.getNumericCellValue() != 0;
      case BOOLEAN:
        return cell.getBooleanCellValue();
      case FORMULA:
        return getCellBoolean(fe.evaluate(cell));
      default:
        return null;
    }
  }

  public static Boolean getCellBoolean(CellValue cell) {
    switch (cell.getCellTypeEnum()) {
      case STRING:
        return !cell.getStringValue().trim().isEmpty();
      case NUMERIC:
        return cell.getNumberValue() != 0;
      case BOOLEAN:
        return cell.getBooleanValue();
      default:
        return null;
    }
  }

  public static Date getCellDate(Cell cell, FormulaEvaluator fe) {
    switch (cell.getCellTypeEnum()) {
      case NUMERIC:
        return cell.getDateCellValue();
      case FORMULA:
        return getCellDate(fe.evaluate(cell));
      default:
        return null;
    }
  }

  public static Date getCellDate(CellValue cell) {
    switch (cell.getCellTypeEnum()) {
      case NUMERIC:
        return DateUtil.getJavaDate(cell.getNumberValue());
      default:
        return null;
    }
  }


}
