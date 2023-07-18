package com.knemerzitski.isikreg.settings;

import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.settings.columns.ComboBoxColumn;
import com.knemerzitski.isikreg.settings.columns.OptionsColumn;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SettingsValidator {

  public static void validate(Settings settings) throws SettingsValidationException {
    // General
    validateNonNegative("settings.general.registerGracePeriod", settings.general.registerGracePeriod);
    validateRequired("settings.smartCard.statusFormat", settings.smartCard.statusFormat);
    validatePositive("settings.general.saveDelay", settings.general.saveDelay);
    validateRequired("settings.general.savePath", settings.general.savePath);

    // SmartCard
    validatePositive("settings.smartCard.externalTerminalFontSize", settings.smartCard.externalTerminalFontSize);

    validateNonNegative("settings.smartCard.noReadersCheckInterval", settings.smartCard.noReadersCheckInterval);
    validateNonNegative("settings.smartCard.readerMissingCheckInterval", settings.smartCard.readerMissingCheckInterval);
    validateNonNegative("settings.smartCard.readersPresentCheckInterval", settings.smartCard.readersPresentCheckInterval);

    validateNonNegative("settings.smartCard.waitForChangeLoopInterval", settings.smartCard.waitForChangeLoopInterval);
    validateNonNegative("settings.smartCard.waitBeforeReadingCard", settings.smartCard.waitBeforeReadingCard);
    validateNonNegative("settings.smartCard.cardReadingFailedRetryInterval", settings.smartCard.cardReadingFailedRetryInterval);
    validateNonNegative("settings.smartCard.cardReadingAttemptsUntilGiveUp", settings.smartCard.cardReadingAttemptsUntilGiveUp);

    // Columns
    List<Column> columns = settings.columns;
    Optional<Column> columnEmptyLabel = columns.stream().filter(c -> (c.label == null || c.label.trim().isEmpty())).findFirst();
    if (columnEmptyLabel.isPresent()) {
      throwException("Peab olema täidetud", "column.label", columnEmptyLabel.get().label);
    }

    validateColumnRequired(settings, Column.Id.REGISTERED);
    validateColumnGroup(settings, Column.Id.REGISTERED, Column.Group.REGISTRATION);
    validateColumnType(settings, Column.Id.REGISTERED, Column.Type.CHECKBOX);
    validateColumnFormNotAllowed(settings, Column.Id.REGISTERED);
    validateColumnStatisticsFalse(settings, Column.Id.REGISTERED);

    validateColumnRequired(settings, Column.Id.REGISTRATION_TYPE);
    validateColumnGroup(settings, Column.Id.REGISTRATION_TYPE, Column.Group.REGISTRATION);
    validateColumnTypeFixedOptions(settings, Column.Id.REGISTRATION_TYPE);
    validateColumnFormRequired(settings, Column.Id.REGISTRATION_TYPE);
    validateColumnStatisticsFalse(settings, Column.Id.REGISTRATION_TYPE);

    validateColumnRequired(settings, Column.Id.REGISTER_DATE);
    validateColumnGroup(settings, Column.Id.REGISTER_DATE, Column.Group.REGISTRATION);
    validateColumnType(settings, Column.Id.REGISTER_DATE, Column.Type.DATE);
    validateColumnTableEditableFalse(settings, Column.Id.REGISTER_DATE);
    validateColumnStatisticsFalse(settings, Column.Id.REGISTER_DATE);

    validateColumnRequired(settings, Column.Id.PERSONAL_CODE);
    validateColumnGroup(settings, Column.Id.PERSONAL_CODE, Column.Group.PERSON);
    validateColumnAnyValue(settings, Column.Id.PERSONAL_CODE);
    validateColumnFormRequired(settings, Column.Id.PERSONAL_CODE);
    validateColumnStatisticsFalse(settings, Column.Id.PERSONAL_CODE);

    Optional<OptionsColumn> columnEmptyOptionsLabel = columns.stream().filter(c -> c instanceof OptionsColumn).map(c -> (OptionsColumn) c)
        .filter(c -> c.options.stream().anyMatch(o -> !o.hasLabel())).findFirst();
    if (columnEmptyOptionsLabel.isPresent()) {
      throwException("Peab olema täidetud", "column.options.label", columnEmptyOptionsLabel.get().label);
    }

    validateNoDuplicates("column.id", columns.stream().filter(c -> c.id != Column.Id.CUSTOM).map(c -> c.id).collect(Collectors.toList()));
    validateNoDuplicates("column.id", columns.stream().filter(c -> c.id == Column.Id.CUSTOM).map(c -> c.customId).collect(Collectors.toList()));
    validateNoDuplicates("column.label", columns.stream().map(c -> c.getLabel().toLowerCase()).collect(Collectors.toList()));
  }

  private static void validateColumnTypeFixedOptions(Settings settings, Column.Id id) throws SettingsValidationException {
    Column col = settings.getColumn(id);
    if (!(col instanceof OptionsColumn)) {
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"type\" peab olema \"RADIO\" või \"COMBOBOX\"!", id));
    }
    if(col instanceof ComboBoxColumn && ((ComboBoxColumn)col).form.editable){
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"form.editable\" peab olema false!", id));
    }
  }

  private static void validateColumnAnyValue(Settings settings, Column.Id id) throws SettingsValidationException {
    Column col = settings.getColumn(id);
    if(col.type == Column.Type.TEXT) return;
    if(col.type == Column.Type.COMBOBOX){
      ComboBoxColumn combo = (ComboBoxColumn)col;
      if(combo.form.editable) return;
    }
    if (col instanceof OptionsColumn) {
      OptionsColumn options = (OptionsColumn)col;
      if(options.getOptionValues().isEmpty()){
        throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"options\" peab sisaldama vähemalt ühte väärtust!", id));
      }
      return;
    }
    throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"type\" peab olema \"TEXT\", \"COMBOBOX\" või \"RADIO\"!", id));
  }

  private static void validateColumnStatisticsFalse(Settings settings, Column.Id id) throws SettingsValidationException {
    if(settings.getColumn(id).statistics)
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"statistics\" peab olema false!", id));
  }


  private static void validateColumnType(Settings settings, Column.Id id, Column.Type type) throws SettingsValidationException {
    if(settings.getColumn(id).type != type)
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"type\" peab olema \"%s\"!", id, type));
  }

  private static void validateColumnTypes(Settings settings, Column.Id id, Column.Type...types) throws SettingsValidationException {
    Column col = settings.getColumn(id);
    if(Stream.of(types).noneMatch(t -> col.type == t)){
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"type\" peab olema %s!", id,
          Stream.of(types).map(t -> "\"" + t + "\"").collect(Collectors.joining(", "))));
    }

  }

  private static void validateColumnGroup(Settings settings, Column.Id id, Column.Group group) throws SettingsValidationException {
    if(settings.getColumn(id).group != group)
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"group\" peab olema \"%s\"!", id, group));
  }

  private static void validateColumnTableEditableFalse(Settings settings, Column.Id id) throws SettingsValidationException {
    if(settings.getColumn(id).table.editable)
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"table.editable\" peab olema false!", id));
  }

  private static void validateColumnFormRequired(Settings settings, Column.Id id) throws SettingsValidationException {
    if(!settings.getColumn(id).isFormRequired())
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"form.required\" peab olema true!", id));
  }

  private static void validateColumnFormNotAllowed(Settings settings, Column.Id id) throws SettingsValidationException {
    if(settings.getColumn(id).hasForm())
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" \"form\" peab olema false!", id));
  }

  private static void validateColumnRequired(Settings settings, Column.Id id) throws SettingsValidationException {
    if (settings.getColumn(id) == null) {
      throw new SettingsValidationException(String.format("Veerg ID \"%s\" on kohustuslik!", id));
    }
  }

  private static void validateRequired(String name, Object value) throws SettingsValidationException {
    if (value == null || (value instanceof String && ((String) value).isEmpty()))
      throwException("Peab olema täidetud", name, value);
  }

  private static void validateNonNegative(String name, Number value) throws SettingsValidationException {
    if (value.intValue() < 0)
      throwException("Ei tohi olla negatiivne", name, value);
  }

  private static void validatePositive(String name, Number value) throws SettingsValidationException {
    if (value.intValue() <= 0)
      throwException("Peab olema positiivne", name, value);
  }


  private static void validateNoDuplicates(String name, List<Object> list) throws SettingsValidationException {
    for (int i = 0; i < list.size(); i++) {
      Object li = list.get(i);
      if (li == null)
        continue;
      for (int j = 0; j < list.size(); j++) {
        if (i == j)
          continue;
        Object lj = list.get(j);
        if (lj == null)
          continue;
        if (li.equals(lj))
          throwException("Peab olema unikaalne", name, li);
      }
    }
  }

  private static void throwException(String message, String name, Object value, Object... args) throws SettingsValidationException {
    if (value instanceof String)
      value = "\"" + value + "\"";
    throw new SettingsValidationException(String.format("Seadistuse viga \"%s\": %s\n" + message, name, value, args));
  }


  private SettingsValidator() {
  }

  public static class SettingsValidationException extends Exception {
    public SettingsValidationException() {
    }

    public SettingsValidationException(String message) {
      super(message);
    }
  }

}
