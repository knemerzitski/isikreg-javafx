package com.knemerzitski.isikreg.settings;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.knemerzitski.isikreg.gson.GsonBooleanProperty;
import com.knemerzitski.isikreg.gson.GsonDateProperty;
import com.knemerzitski.isikreg.gson.GsonStringProperty;
import com.knemerzitski.isikreg.gson.StringAdapter;
import com.knemerzitski.isikreg.settings.adapters.BooleanObjectExpandAdapter;
import com.knemerzitski.isikreg.settings.columns.*;
import com.knemerzitski.isikreg.ui.DialogHandler;
import com.knemerzitski.isikreg.ui.status.VariableStatusMessages;
import com.knemerzitski.isikreg.utils.StringUtils;
import io.gsonfire.GsonFireBuilder;
import javafx.beans.property.Property;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.knemerzitski.isikreg.settings.columns.Column.Group.PERSON;
import static com.knemerzitski.isikreg.settings.columns.Column.Group.REGISTRATION;
import static com.knemerzitski.isikreg.settings.columns.Column.Id.*;

public class Settings {

  private static final Column[] DEFAULT_COLUMNS = {
      new CheckBoxColumn(REGISTRATION, REGISTERED, Column.Type.CHECKBOX, "Registreeritud", false, false, true),
      new RadioColumn(REGISTRATION, REGISTRATION_TYPE, Column.Type.RADIO, "Registreerimise\ntüüp",
          true, true, false, Arrays.asList(
          new OptionsColumn.Option("Sisse"),
          new OptionsColumn.Option("Välja")
      ), Orientation.HORIZONTAL),
      new DateColumn(REGISTRATION, REGISTER_DATE, Column.Type.DATE, "Registreerimise\naeg", false, false, false, "dd.MM HH:mm"),
      new TextColumn(PERSON, PERSONAL_CODE, Column.Type.TEXT, "Isikukood", true, true, false),
      new TextColumn(PERSON, LAST_NAME, Column.Type.TEXT, "Perekonnanimi", true, false, false),
      new TextColumn(PERSON, FIRST_NAME, Column.Type.TEXT, "Eesnimi", true, false, false),


//      new Column(PERSON, SEX, Column.Type.RADIO, "Sugu", true, true, true),
//      new Column(PERSON, CITIZENSHIP, Column.Type.TEXT, "Kodakondsus", true, false, true),
//      new Column(PERSON, DATE_OF_BIRTH, Column.Type.DATE, "Sünniaeg", true, false, true),
//      new Column(PERSON, PLACE_OF_BIRTH, Column.Type.TEXT, "Sünnikoht", true, false, true),
//      new Column(PERSON, DOCUMENT_NR, Column.Type.TEXT, "Dokumendi number", true, false, true),
//      new Column(PERSON, EXPIRY_DATE, Column.Type.DATE, "Kehtiv kuni", true, false, true),
//      new Column(PERSON, DATE_OF_ISSUANCE, Column.Type.DATE, "Välja antud", true, false, true),
//      new Column(PERSON, PLACE_OF_ISSUANCE, Column.Type.TEXT, "Välja andmise koht", true, false, true),
//      new Column(PERSON, TYPE_OF_RESIDENCE_PERMIT, Column.Type.TEXT, "Elamisloa tüüp", true, false, true),
//      new Column(PERSON, NOTES_LINE1, Column.Type.TEXT, "Märkused 1", true, false, true),
//      new Column(PERSON, NOTES_LINE2, Column.Type.TEXT, "Märkused 2", true, false, true),
//      new Column(PERSON, NOTES_LINE3, Column.Type.TEXT, "Märkused 3", true, false, true),
//      new Column(PERSON, NOTES_LINE4, Column.Type.TEXT, "Märkused 4", true, false, true),
//      new Column(PERSON, NOTES_LINE5, Column.Type.TEXT, "Märkused 5", true, false, true),
  };

  private static final Gson GSON = new GsonFireBuilder()
      .enumDefaultValue(Rule.class, Rule.ALLOW)
      .enumDefaultValue(ColumnResizePolicy.class, ColumnResizePolicy.UNCONSTRAINED)
      .enumDefaultValue(Column.Group.class, PERSON)
      .enumDefaultValue(Column.Id.class, CUSTOM)
      .enumDefaultValue(Column.Type.class, Column.Type.TEXT)
      .enumDefaultValue(Column.Merge.Rule.class, Column.Merge.Rule.OVERWRITE_ON_EMPTY)
      .enumDefaultValue(Orientation.class, Orientation.VERTICAL)
      .registerTypeSelector(Column.class, jsonElement -> {
        JsonObject obj = jsonElement.getAsJsonObject();
        if (obj.has("type")) {
          return Column.Type.valueOf(obj.get("type").getAsString()).getColumnClass();
        }
        return Column.Type.TEXT.getColumnClass();
      })
      .registerPostProcessor(Column.class, new Column.ColumnPostProcessor())
      .registerPostProcessor(ComboBoxColumn.ComboBoxForm.class, new ComboBoxColumn.ComboBoxForm.ComboBoxFormPostProcessor())
      .createGsonBuilder()
      .registerTypeHierarchyAdapter(Collection.class, new CollectionAdapter())
      .registerTypeAdapterFactory(new SettingsTypeAdapterFactory())
      .registerTypeAdapter(String.class, new StringAdapter())
//      .registerTypeAdapter(Pattern.class, new PatternAdapter())
      .setPrettyPrinting()
      .create();


  static class CollectionAdapter implements JsonSerializer<List<?>> {
    @Override
    public JsonElement serialize(List<?> src, Type typeOfSrc, JsonSerializationContext context) {
      if (src == null || src.isEmpty()) // exclusion is made here
        return null;

      JsonArray array = new JsonArray();

      for (Object child : src) {
        JsonElement element = context.serialize(child);
        array.add(element);
      }

      return array;
    }

  }

  static class SettingsTypeAdapterFactory implements TypeAdapterFactory {
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
      if (type.getType() == Column.Merge.class) {
        return (TypeAdapter<T>) new Column.Merge.MergeAdapter((TypeAdapter<Column.Merge>) delegate);
      } else if (type.getType() == OptionsColumn.Option.class) {
        return (TypeAdapter<T>) new OptionsColumn.Option.OptionAdapter((TypeAdapter<OptionsColumn.Option>) delegate);
      } else if (
          type.getType() == Column.Table.class ||
              type.getType() == QuickRegistrationButtons.class ||
              Form.class.isAssignableFrom(type.getRawType())) {
        return new BooleanObjectExpandAdapter<>(delegate);
      }

      return delegate;
    }
  }

  public static Settings newDefault() {
    return newDefault(null);
  }

  public static Settings newDefault(Path path) {
    Settings settings = new Settings();
    settings.path = path;
    // Add default columns only if settings are missing
    settings.columns.addAll(Arrays.asList(DEFAULT_COLUMNS));
    return settings;
  }

  public static Settings readFromJson(Path path) throws IOException {
    Settings settings;
    try (InputStreamReader isr = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
      settings = GSON.fromJson(isr, Settings.class);
      settings.path = path;
    } catch (JsonSyntaxException e) {
      throw new JsonSyntaxException(e.getMessage() + "\n\"" + path + "\" vale JSON struktuur", e);
    } catch (FileNotFoundException | NoSuchFileException e) {
      // Add default columns only since settings are missing
      settings = newDefault(path);
    }

//    // Add missing default columns
//    final Settings finalSettings = settings;
//    List<Column> defaultColumns = new ArrayList<>();
//    Arrays.stream(DEFAULT_COLUMNS).forEach(defaultColumn -> {
//      if (finalSettings.columns.stream()
//          .noneMatch(c -> defaultColumn.id.equals(c.id))) {
//        defaultColumns.add(defaultColumn);
//      }
//    });
//    settings.columns.addAll(0, defaultColumns);

//    // Don't save REGISTERED column as it can be derived from REGISTER_DATE column
//    Column col = settings.getColumn(REGISTERED);
//    if(col != null){
//      col.save = false;
//    }

    return settings;
  }

  public enum Orientation {
    VERTICAL, HORIZONTAL
  }

  public enum Rule {
    ALLOW, CONFIRM, DENY
  }

  public enum ColumnResizePolicy {
    UNCONSTRAINED, CONSTRAINED
  }

  public static class QuickRegistrationButtons {

    public boolean showSelectedPerson = true;

  }

  public static class General {

    public String savePath = "./isikreg";
    public long saveDelay = 100; // milliseconds > 0
    public boolean saveCompressedZip = true;
    public boolean errorLogging = true;
    public boolean smoothFont = true;

    public String defaultRegistrationType;

    public Rule registerDuringGracePeriod = Rule.CONFIRM;
    public long registerGracePeriod = 10 * 60 * 1000; // >= 0, milliseconds

    public Rule registerSameTypeInRow = Rule.ALLOW;

    public boolean insertPerson = true;
    public boolean updatePerson = true;
    public boolean deletePerson = true;

    public boolean warnDuplicateRegistrationDate = true;

    public boolean tableContextMenu = true;

    public QuickRegistrationButtons quickRegistrationButtons = new QuickRegistrationButtons();

    public ColumnResizePolicy columnResizePolicy = ColumnResizePolicy.UNCONSTRAINED;

    public boolean currentSettingsMenuItem = false;

  }

  public static class Excel {
    public String sheetName = "Nimekiri";
    public String exportDateTimeFormat = "dd.mm.yyyy hh:mm";
    public String exportDateFormat = "dd.mm.yyyy";
    public boolean exportAutoSizeColumns = true;
  }

  public static class SmartCard {

    public VariableStatusMessages statusFormat = new VariableStatusMessages(
        "{FIRST_NAME} {LAST_NAME}\n@event"
    );
    public long showSuccessStatusDuration = 10000; // milliseconds, negative means show success result until next card is put in

    public int externalTerminalFontSize = 50; // > 0
    public boolean enableCardPresentIndicator = true; // default true

    // Rules
    public Rule registerExpiredCards = Rule.CONFIRM;
    public Rule registerPersonNotInList = Rule.ALLOW; // Allow person that is not in list

    public boolean quickNewPersonRegistration = false; // Register new person without showing form
    public boolean quickExistingPersonRegistration = false;

    public long waitForChangeLoopInterval = 0; // milliseconds, >= 0, How often program checks for card insert/removal changes. If 0 then program waits indefinitely until change happens

    // Card
    public long waitBeforeReadingCard = 250; // milliseconds, >= 0
    public long cardReadingFailedRetryInterval = 2000; // milliseconds, >= 0
    public int cardReadingAttemptsUntilGiveUp = 4; // >= 0

    // Card Reader
    public long noReadersCheckInterval = 2000; // milliseconds, >= 0
    public long readerMissingCheckInterval = 2000; // milliseconds, >= 0
    public long readersPresentCheckInterval = 10000; // milliseconds, >= 0

  }

  public transient DialogHandler dialogHandler = new DialogHandler();

  private transient Path path; // Used for saving to same path as read from

  public General general = new General();
  public Excel excel = new Excel();
  public SmartCard smartCard = new SmartCard();
  public List<Column> columns = new ArrayList<>();

  public Settings() {
  }

  public Settings(Path path) {
    this.path = path;
  }

  public void writeAsJson(Path path) throws IOException {

    try (OutputStreamWriter osw = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
      GSON.toJson(this, osw);
      osw.flush();
    }
  }

  public JsonElement toJsonTree() {
    return GSON.toJsonTree(this);
  }

  public void save(@NotNull Path path) throws IOException {
    writeAsJson(path);
  }

  /**
   * Saves settings only if path doesn't exist
   */
  public void saveNew(@NotNull Path path) throws IOException {
    if (!Files.exists(path)) {
      save(path);
    }
  }

  public void saveNew() throws IOException {
    if (path == null)
      throw new NullPointerException("Settings path is not defined");
    if (!Files.exists(path)) {
      save(path);
    }
  }

  public String getDefaultRegistrationType() {
    List<String> types = getRegistrationTypes();
    if (general.defaultRegistrationType != null && types.stream()
        .anyMatch(t -> t.toLowerCase().trim().equals(general.defaultRegistrationType.trim().toLowerCase())))
      return general.defaultRegistrationType;
    return !types.isEmpty() ? types.get(0) : null;
  }

  public List<String> getRegistrationTypes() {
    Column registrationTypeColumn = getColumn(Column.Id.REGISTRATION_TYPE);
    if (registrationTypeColumn instanceof OptionsColumn) {
      OptionsColumn options = (OptionsColumn) registrationTypeColumn;
      return options.getOptionValues();
    }
    return Collections.emptyList();
  }

  public List<Column> getFormColumns() {
    return columns.stream().filter(column -> column.hasForm() && column.hasLabel()).collect(Collectors.toList());
  }

  public List<Column> getColumnsByGroup(Column.Group group) {
    return columns.stream().filter(column -> column.group == group).collect(Collectors.toList());
  }

  public Column getColumn(Column.Id id) {
    return columns.stream()
        .filter(column -> column.id == id)
        .findFirst().orElse(null);
  }

  public Column parseColumn(String value) {
    Column.Id id = Column.Id.get(value);
    if (id != CUSTOM) {
      return getColumn(id);
    } else {
      // Find by custom id
      Column column = columns.stream()
          .filter(c -> c.customId != null && c.customId.equals(value))
          .findFirst().orElse(null);
      if (column == null) {
        // Didn't find by id, try label
        return columns.stream()
            .filter(c -> c.label != null && c.label.trim().equalsIgnoreCase(value))
            .findFirst().orElse(null);
      } else {
        return null;
      }
    }
  }

  public List<TypeGroupedColumn> registrationTypeGroupColumns() {
    List<Column> registrationColumns = columns.stream().filter(c -> c.group == Column.Group.REGISTRATION)
        .collect(Collectors.toList());
    Column regColumn = registrationColumns.stream().filter(c -> c.id == Column.Id.REGISTERED).findFirst().orElse(null);
    Column regTypeColumn = registrationColumns.stream().filter(c -> c.id == Column.Id.REGISTRATION_TYPE).findFirst().orElse(null);
    Column regDateCol = registrationColumns.stream().filter(c -> c.id == Column.Id.REGISTER_DATE).findFirst().orElse(null);
    registrationColumns.remove(regColumn);
    registrationColumns.remove(regTypeColumn);

    if (regTypeColumn != null && regDateCol != null) {
      List<String> extraColumnNames = new ArrayList<>();
      if (regTypeColumn instanceof OptionsColumn) {
        List<OptionsColumn.Option> options = ((OptionsColumn) regTypeColumn).options;
        extraColumnNames.addAll(options.stream().map(o -> o.label).collect(Collectors.toList()));
      } else {
        extraColumnNames.add(regTypeColumn.getLabel());
      }

      return extraColumnNames.stream().flatMap(label -> registrationColumns.stream().map(regCol -> {
        TypeGroupedColumn col = new TypeGroupedColumn();
        col.type = label;
        col.source = regCol;
        col.label = StringUtils.firstCharCapitalize(label + " " + regCol.label);
        return col;
      })).collect(Collectors.toList());
    } else {
      return null;
    }
  }


  public Property<?> newProperty(Column.Id id) {
    Column column = getColumn(id);
    return column != null ? newProperty(column) : null;
  }

  public Property<?> newProperty(Column column) {
    Class<? extends Property<?>> klass = columnPropertyClass(column);
    if (klass == null)
      return null;
    try {
      return klass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      return null;
    }
  }

  public Class<? extends Property<?>> columnPropertyClass(Column column) {
    switch (column.type) {
      case TEXT:
      case RADIO:
      case COMBOBOX:
        return GsonStringProperty.class;
      case CHECKBOX:
        return GsonBooleanProperty.class;
      case DATE:
        return GsonDateProperty.class;
      default:
        return null;
    }
  }

  public <T extends Property<?>> T newProperty(Column.Id id, Class<T> klass) {
    Property<?> property = newProperty(id);
    if (klass.isInstance(property)) {
      return klass.cast(property);
    }
    return null;
  }

}

