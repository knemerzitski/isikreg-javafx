package com.knemerzitski.isikreg;

import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.field.ObjFieldValueSet;
import com.knemerzitski.isikreg.person.Person;
import com.knemerzitski.isikreg.person.Registration;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.smartcard.FakeTerminalSimulator;
import com.sun.javafx.scene.control.skin.ContextMenuContent;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matcher;
import org.testfx.api.FxRobot;
import org.testfx.api.FxRobotException;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.matcher.control.TableViewMatchers;
import org.testfx.service.query.EmptyNodeQueryException;
import org.testfx.service.query.NodeQuery;
import org.testfx.util.WaitForAsyncUtils;

import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.knemerzitski.isikreg.settings.Settings.Rule.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;

public class Actions {

  private final IsikReg isikReg;
  private final FakeTerminalSimulator terminalSimulator;
  private final FxRobot robot;
  private final Random rand;
  private int populateCounter;

  private long timeout = TimeUnit.SECONDS.toNanos(1);

  public Actions(IsikReg isikReg, FakeTerminalSimulator terminalSimulator, FxRobot robot, long seed) {
    this.isikReg = isikReg;
    this.terminalSimulator = terminalSimulator;
    this.robot = robot;
    rand = new Random(seed);
  }

  public Random getRandom() {
    return rand;
  }

  public void setTimeout(long timeout, TimeUnit unit) {
    this.timeout = unit.toNanos(timeout);
  }

  // ###################################### Registered count pane #########################################

  @SuppressWarnings("unchecked")
  public void assertRegisteredCount(Stream<Integer> counts) {
    waitForFxEvents(5);
    Parent windowNode = robot.listWindows().get(0).getScene().getRoot();
    assertThat(robot.lookup(equalTo(windowNode))
            .lookup(".registered-count-pane")
            .lookup(instanceOf(Labeled.class)).queryAllAs(Labeled.class),
        hasItems(counts
            .map(count -> LabeledMatchers
                .hasText(containsString(String.valueOf(count)))).toArray(Matcher[]::new)));
  }


  // ###################################### Menu "Fail" #########################################

  public void clickFileMenu() {
    clickMenu("Fail");
  }

  public void clickImportMenuItem() {
    clickMenuItem("Import");
  }

  public void clickExportMenuItem() {
    clickMenuItem("Eksport");
  }

  public void clickExportGroupedMenuItem() {
    clickMenuItem("Eksport \\(Grupeeri registreerimise tüüp\\)");
  }


  // ###################################### Menu "Nimekiri" #########################################

  public void clickListMenu() {
    clickMenu("Nimekiri");
  }

  public void clickNewRegistrationMenuItem() {
    clickMenuItem("Uus registreerimine");
  }

  public void clickClearAllRegistrationsMenuItem() {
    clickMenuItem("Tühista kõik registreerimised");
  }

  public void clickDeleteAllPeopleRegistrationsMenuItem() {
    clickMenuItem("Kustuta kõik isikud ja registreerimised");
  }


  // ###################################### Menu "Valitud Read" #########################################

  public void clickSelectedRowsMenu() {
    clickMenu("Valitud read");
  }

  public void clickSelectedPersonNewTypeRegistrationMenuItem() {
    clickMenuItem("Uut tüüpi registreerimine");
  }

  public void clickEditSelectPersonOrRegistrationMenuItem() {
    clickMenuItem("Muuda (isikut|registreeringut|isikut/registreeringut)");
  }

  public void clickEditSelectedPersonPersonalCodeMenuItem() {
    clickMenuItem("Muuda isikukoodi");
  }

  public void clickClearSelectedRegistrationsMenuItem() {
    clickMenuItem("Tühista registreerimised");
  }

  public void clickDeleteSelectedPeopleMenuItem() {
    clickMenuItem("Kustuta isikud");
  }


  // ###################################### Menu "Terminali aknad" #########################################

  public void clickTerminalWindowsMenu() {
    clickMenu("Terminali aknad");
  }

  public void clickAllTerminalsMenuItem() {
    clickMenuItem("Kõik terminalid");
  }


  // ####################################### Bottom right buttons ##########################################

  public void clickNewRegistrationButton() {
    clickButton("Uus registreerimine");
  }

  public void clickQuickCancelRegistrationButton() {
    clickButton("Tühista");
  }

  public void clickQuickRegisterButton(String type) {
    clickButton("Registreeri " + type.toLowerCase());
  }

  // ########################################### Dialogs ######################################################

  public void assertConfirmGracePeriod(boolean confirmYes, Registration existingRegistration, String newRegistrationType) {
    assertConfirmDialog(confirmYes,
        "On (\\d)+ (sekund(it)?|minut(it)?|tund(i)?) tagasi " + existingRegistration.getRegistrationType().toLowerCase() + " registreeritud\\.\n" +
            "Kas registreerin " + newRegistrationType.toLowerCase() + "\\?",
        existingRegistration.getRegistrationType() + " " + personDialogSubText(existingRegistration.getPerson()));
  }

  public void assertConfirmGracePeriodContinue(boolean confirmYes, Registration existingRegistration) {
    assertConfirmDialog(confirmYes,
        "On (\\d)+ (sekund(it)?|minut(it)?|tund(i)?) tagasi " + existingRegistration.getRegistrationType().toLowerCase() + " registreeritud\\.\n" +
            "Kas jätkan registreerimisega\\?",
        existingRegistration.getRegistrationType() + " " + personDialogSubText(existingRegistration.getPerson()));
  }

  public void assertWarningGracePeriod(Registration existingRegistration) {
    assertWarningDialog(
        "On " + existingRegistration.getRegistrationType().toLowerCase() + " registreeritud!\n" +
            "Registreerida saab (\\d)+ (sekundi|minuti|tunni) pärast\\.",
        existingRegistration.getRegistrationType() + " " + personDialogSubText(existingRegistration.getPerson()));
  }

  public boolean assertDuringGracePeriodRegister(ObjFieldValueSet conf, Registration existingRegistration, String newRegistrationType) {
    Settings settings = isikReg.getSettings();
    switch (settings.general.registerDuringGracePeriod) {
      case CONFIRM:
        boolean confirmYes = conf.getByName("general.registerDuringGracePeriod").getDataAsBoolean();
        assertConfirmGracePeriod(confirmYes, existingRegistration, newRegistrationType);
        if (!confirmYes) return false;
        break;
      case DENY:
        assertWarningGracePeriod(existingRegistration);
        return false;
    }
    return true;
  }

  public Settings.Rule assertDuringGracePeriodContinueRegister(ObjFieldValueSet conf, Registration existingRegistration) {
    Settings settings = isikReg.getSettings();
    switch (settings.general.registerDuringGracePeriod) {
      case CONFIRM:
        boolean confirmYes = conf.getByName("general.registerDuringGracePeriod").getDataAsBoolean();
        assertConfirmGracePeriodContinue(confirmYes, existingRegistration);
        if (!confirmYes) return CONFIRM;
        break;
      case DENY:
        return DENY;
    }
    return ALLOW;
  }

  public boolean assertDuringGracePeriod(ObjFieldValueSet conf, Person existingPerson, Registration existingRegistration,
                                         String newRegistrationType) {
    boolean register = assertDuringGracePeriodRegister(conf, existingRegistration, newRegistrationType);
    assertExistingPersonJustRegistration(register, existingPerson, existingRegistration, newRegistrationType);
    return register;
  }

  public boolean assertDuringGracePeriodSameTypeInRow(ObjFieldValueSet conf, Person existingPerson, Registration existingRegistration, String newRegistrationType) {
    Settings settings = isikReg.getSettings();

    Registration latestRegistration = existingPerson.getLatestRegisteredRegistration();

    boolean registered = true;
    switch (settings.general.registerDuringGracePeriod) {
      case ALLOW:
        switch (settings.general.registerSameTypeInRow) {
          case CONFIRM:
            boolean confirmYes = conf.getByName("general.registerSameTypeInRow").getDataAsBoolean();
            assertConfirmSameTypeInRow(confirmYes, existingRegistration, newRegistrationType);
            if (!confirmYes) registered = false;
            break;
          case DENY:
            assertWarningSameTypeInRow(existingRegistration);
            registered = false;
            break;
        }
        break;
      case CONFIRM:
        switch (settings.general.registerSameTypeInRow) {
          case ALLOW:
          case CONFIRM:
            boolean confirmYes = conf.getByName("general.registerDuringGracePeriod").getDataAsBoolean();
            assertConfirmGracePeriod(confirmYes, existingRegistration, newRegistrationType);
            if (!confirmYes) registered = false;
            break;
          case DENY:
            assertWarningSameTypeInRow(existingRegistration);
            registered = false;
            break;
        }
        break;
      case DENY:
        switch (settings.general.registerSameTypeInRow) {
          case ALLOW:
          case CONFIRM:
            assertWarningGracePeriod(existingRegistration);
            registered = false;
            break;
          case DENY:
            assertWarningSameTypeInRow(existingRegistration);
            registered = false;
            break;
        }
        break;
    }

    assertExistingPersonJustRegistration(registered, existingPerson, existingRegistration, newRegistrationType);
    return registered;
  }

  public void assertConfirmSameTypeInRow(boolean confirmYes, Registration existingRegistration, String newRegistrationType) {
    assertConfirmDialog(confirmYes,
        "On juba " + existingRegistration.getRegistrationType().toLowerCase() + " registreeritud!\n" +
            "Kas registreerin veelkord " + newRegistrationType.toLowerCase() + "\\?",
        existingRegistration.getRegistrationType() + " " + personDialogSubText(existingRegistration.getPerson()));
  }

  public void assertWarningSameTypeInRow(Registration existingRegistration) {
    assertWarningDialog(
        "On juba " + existingRegistration.getRegistrationType().toLowerCase() + " registreeritud!\n" +
            "Järjest topelt sama registreerimine ei ole lubatud\\.",
        existingRegistration.getRegistrationType() + " " + personDialogSubText(existingRegistration.getPerson()));
  }

  public boolean assertSameTypeInRowContinue(Registration existingRegistration) {
    Settings settings = isikReg.getSettings();
    if (settings.general.registerSameTypeInRow == DENY) {
      assertWarningSameTypeInRow(existingRegistration);
      return false;
    }
    return true;
  }

  public boolean assertSameTypeInRowRegister(ObjFieldValueSet conf, Registration existingRegistration, String newRegistrationType) {
    Settings settings = isikReg.getSettings();
    boolean register = true;
    switch (settings.general.registerSameTypeInRow) {
      case CONFIRM:
        boolean confirmYes = conf.getByName("general.registerSameTypeInRow").getDataAsBoolean();
        assertConfirmSameTypeInRow(confirmYes, existingRegistration, newRegistrationType);
        if (!confirmYes) register = false;
        break;
      case DENY:
        assertWarningSameTypeInRow(existingRegistration);
        register = false;
        break;
    }
    return register;
  }

  public boolean assertSameTypeInRow(ObjFieldValueSet conf, Person existingPerson, Registration existingRegistration, String newRegistrationType) {
    boolean register = assertSameTypeInRowRegister(conf, existingRegistration, newRegistrationType);
    assertExistingPersonJustRegistration(register, existingPerson, existingRegistration, newRegistrationType);
    return register;
  }

  public void assertConfirmCancelSelectedRegistration(boolean confirmYes, Registration registration) {
    Person person = registration.getPerson();
    assertConfirmDialog(confirmYes,
        "Oled kindel, et tahad valitud registreerimist tühistada\\?",
        registration.getRegistrationType(),
        registration.getRegisteredDate().toString(),
        person.getPersonalCode(),
        person.getLastName(),
        person.getFirstName());
  }

  public void assertConfirmCancelSelectedRegistrations(boolean confirmYes, List<Registration> registrations) {
    Stream<String> rows = Stream.of("Oled kindel, et tahad valitud registreerimisi tühistada\\?");
    rows = Stream.concat(rows, registrations.stream().flatMap(r -> Stream.of(r.getRegistrationType(), r.getRegistrationType(),
        r.getPerson().getPersonalCode(), r.getPerson().getLastName(), r.getPerson().getFirstName())));
    assertConfirmDialog(confirmYes, rows);
  }

  public void assertConfirmDeleteSelectedPeople(boolean confirmYes, List<Person> personList) {
    Stream<String> rows = Stream.of("Oled kindel, et tahad valitud isikud kustutada\\?");
    rows = Stream.concat(rows, personList.stream().flatMap(p -> Stream.of(p.getPersonalCode(), p.getLastName(), p.getFirstName())));
    assertConfirmDialog(confirmYes, rows);
  }

  public void assertConfirmDialog(boolean answerYes, String... labelsRegex) {
    assertConfirmDialog(answerYes, Stream.of(labelsRegex));
  }

  public void assertConfirmDialog(boolean answerYes, Stream<String> labelsRegex) {
    String title = "Kinnitus";
    NodeQuery confirmWindow = robot.lookup(equalTo(waitForWindowShowing(title)));

    //noinspection unchecked
    assertThat(confirmWindow.lookup(instanceOf(Labeled.class)).queryAllAs(Labeled.class),
        hasItems(labelsRegex
            .map(regex -> LabeledMatchers.hasText(matchesPattern(regex))).toArray(Matcher[]::new))
    );

    if (answerYes) {
      clickButton("JAH", title);
    } else {
      clickButton("EI", title);
    }
  }

  public void assertWarningDialog(String... labelsRegex) {
    String title = "Hoiatus";
    NodeQuery warningDialog = robot.lookup(equalTo(waitForWindowShowing(title)));

    //noinspection unchecked
    assertThat(warningDialog.lookup(instanceOf(Labeled.class)).queryAllAs(Labeled.class),
        hasItems(Stream.of(labelsRegex)
            .map(regex -> LabeledMatchers.hasText(matchesPattern(regex))).toArray(Matcher[]::new))
    );

    clickButton("OK", title);
  }

  public static String personDialogSubText(Person p) {
    return Stream.of(p.getPersonalCode(), p.getLastName(), p.getFirstName())
        .filter(n -> n != null && !n.isEmpty()).collect(Collectors.joining(" "));
  }


  // ########################################### Dialog forms ######################################################

  public void fillForm(String windowTitleRegex, String... inputs) {
    assertEquals(0, inputs.length % 2, "Inputs length must be divisible by 2");

    NodeQuery windowQuery = robot.lookup(equalTo(window(windowTitleRegex)));

    for (int i = 0; i < inputs.length - 1; i += 2) {
      String name = inputs[i];
      String value = inputs[i + 1];
      robot.from(windowQuery).lookup(LabeledMatchers.hasText(containsString(name))).queryAll().forEach(node -> {
        Parent parent = node.getParent();
        ObservableList<Node> children = parent.getChildrenUnmodifiable();
        Node inputNode = children.get(children.indexOf(node) + 1);
        if (inputNode instanceof TextField) {
          if (!value.equals(((TextField) inputNode).getText())) {
            robot.doubleClickOn(inputNode);
            robot.write(value, 0);
          }
        } else {
          robot.clickOn(robot.from(inputNode).lookup(LabeledMatchers.hasText(value)).queryLabeled());
        }
      });
    }
  }

  public void assertFormValues(String windowTitleRegex, String... inputs) {
    assertEquals(0, inputs.length % 2, "Inputs length must be divisible by 2");

    NodeQuery windowQuery = robot.lookup(equalTo(window(windowTitleRegex)));

    for (int i = 0; i < inputs.length - 1; i += 2) {
      String name = inputs[i];
      String value = inputs[i + 1];
      robot.from(windowQuery).lookup(LabeledMatchers.hasText(containsString(name))).queryAll().forEach(node -> {
        Parent parent = node.getParent();
        ObservableList<Node> children = parent.getChildrenUnmodifiable();
        Node inputNode = children.get(children.indexOf(node) + 1);
        if (inputNode instanceof TextField) {
          assertEquals(value, ((TextField) inputNode).getText());
        } else {
          assertThat(robot.from(inputNode).lookup(instanceOf(Labeled.class)).queryAllAs(Labeled.class),
              hasItem(LabeledMatchers.hasText(value)));
        }
      });
    }
  }


  // ########################################### Person helpers ######################################################

  public Person newPerson(String personalCode) {
    Person p = new Person(isikReg.getSettings());
    p.setPersonalCode(personalCode);
    isikReg.getPersonList().add(p);
    return p;
  }


  // ########################################### Person registered asserts ######################################################

  public void assertNewPersonJustRegistered(String registrationType, String personalCode, String lastName, String firstName) {
    assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
    Registration reg = getSelectedTableRegistration();
    assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), personalCode, lastName, firstName);
  }

  public void assertExistingPersonJustRegistered(Person person, String registrationType) {
    assertPersonRegisteredTimeAgo(person.getPersonalCode(), 1, ChronoUnit.SECONDS);
    Registration reg = getSelectedTableRegistration();
    assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), person.getPersonalCode(), person.getLastName(), person.getFirstName());
    assertEquals(person, reg.getPerson());
  }

  public void assertExistingPersonJustRegistration(boolean registered, Person person, Registration existingRegistration, String registrationType) {
    if (registered) {
      assertExistingPersonJustRegistered(person, registrationType);
      if (person.getLatestRegisteredRegistration() != existingRegistration) {
        assertNotEquals(existingRegistration, getSelectedTableRegistration());
      }
    } else {
      assertTrue(existingRegistration.isRegistered());
      if (person.getLatestRegisteredRegistration() != existingRegistration) { // new registration???
        assertPersonNotRegisteredTimeAgo(person.getPersonalCode(), 1, ChronoUnit.SECONDS);
      }
    }
  }

  public void assertPersonSelectedTableRowNotRegistered(Person person, String registrationType) {
    Registration reg = getSelectedTableRegistration();
    assertEquals(person, reg.getPerson());
    assertTableContainsRow(false, registrationType, null, person.getPersonalCode(), person.getLastName(), person.getFirstName());
    assertFalse(reg.isRegistered());
  }

  public void assertRegistrationRemoved(Person person, Registration registration) {
    assertFalse(person.getRegistrations().contains(registration));
    assertFalse(registration.isRegistered());
    assertTrue(registration.isRemoved());
    assertNull(registration.getPerson());
  }

  public void assertRegistrationExists(Person person, Registration registration) {
    assertTrue(person.getRegistrations().contains(registration));
    assertNotNull(registration.getPerson());
    assertFalse(registration.isRemoved());
  }

  public void assertRegistrationRegistered(Person person, Registration registration) {
    assertTrue(person.getRegistrations().contains(registration));
    assertTrue(registration.isRegistered());
    assertNotNull(registration.getPerson());
    assertFalse(registration.isRemoved());
  }

  public void assertPersonRegisteredTimeAgo(String personalCode, long time, ChronoUnit unit) {
    TableView<Registration> tableView = robot.lookup(instanceOf(TableView.class)).queryTableView();

    Registration reg = tableView.getItems().stream().filter(r -> r.getPerson().getPersonalCode().equals(personalCode)).findFirst().orElse(null);
    assertNotNull(reg, String.format("Person '%s' not found in the table!", personalCode));

    Person person = reg.getPerson();

    reg = person.getLatestRegisteredRegistration();

    assertNotNull(reg, String.format("Person '%s' is not registered!", personalCode));

    if (unit != null) {
      Date date = reg.getRegisteredDate();
      LocalDateTime datePlusDur = date.getLocalDateTime().plus(Duration.of(time, unit));
      assertTrue(datePlusDur.isAfter(LocalDateTime.now()), String.format("Person '%s' didn't register %s ago", personalCode, Duration.of(time, unit)));
    }

    assertThat(tableView, TableViewMatchers.containsRow(true, reg.getRegistrationType(), reg.getRegisteredDate(), personalCode));
  }

  public void assertPersonNotRegisteredTimeAgo(String personalCode, long time, ChronoUnit unit) {
    TableView<Registration> tableView = robot.lookup(".table-view").queryTableView();

    Registration reg = tableView.getItems().stream().filter(r -> r.getPerson().getPersonalCode().equals(personalCode)).findFirst().orElse(null);
    if (reg == null)
      return;

    Person person = reg.getPerson();

    reg = person.getLatestRegisteredRegistration();
    if (reg == null)
      return;

    if (unit != null) {
      Date date = reg.getRegisteredDate();
      LocalDateTime datePlusDur = date.getLocalDateTime().plus(Duration.of(time, unit));
      LocalDateTime now = LocalDateTime.now();
      assertTrue(datePlusDur.isBefore(now), String.format("Person '%s' registerered %s ago", personalCode, Duration.between(now, datePlusDur)));
    } else {
      fail(String.format("Person '%s' is registerered", personalCode));
    }
  }


  // ########################################## Table helpers ###############################

  public void clickTableRegisteredCheckBox(Registration registration) throws ExecutionException, InterruptedException, TimeoutException {
    String registeredLabel = isikReg.getSettings().getColumn(Column.Id.REGISTERED).getLabel();

    TableView<?> tableView = robot.lookup(instanceOf(TableView.class)).queryTableView();

    TableColumn<?, ?> registeredColumn = tableView.getColumns().stream().filter(c -> c.getText().equals(registeredLabel)).findFirst().orElse(null);
    assertNotNull(registeredColumn, String.format("Table column '%s' not found", registeredLabel));

    int rowIndex = tableView.getItems().indexOf(registration);
    assertNotEquals(-1, rowIndex, String.format("Registration '%s' not found in table", registration));

    waitForClickOn(equalTo(waitForQuery(robot.lookup(equalTo(tableView)).lookup((Predicate<Node>) node -> {
      if (!(node instanceof CheckBoxTableCell<?, ?>)) return false;
      CheckBoxTableCell<?, ?> checkBoxCell = (CheckBoxTableCell<?, ?>) node;
      return checkBoxCell.getTableColumn() == registeredColumn &&
          checkBoxCell.getTableRow().getIndex() == rowIndex;
    }))));
  }

  public Registration getSelectedTableRegistration() {
    TableView<Registration> tableView = robot.lookup(instanceOf(TableView.class)).queryTableView();
    return tableView.getSelectionModel().getSelectedItem();
  }

  public List<Registration> getSelectedTableRegistrations() {
    TableView<Registration> tableView = robot.lookup(instanceOf(TableView.class)).queryTableView();
    return tableView.getSelectionModel().getSelectedItems();
  }

  public void selectRegistrationRow(Registration r) {
    try {
      waitForRunOnFxThread(() -> isikReg.focusRegistration(r));
    } catch (TimeoutException e) {
      fail(String.format("Couldn't focus registration '%s'", r));
    }
    waitForFxEvents();
  }

  public void selectRegistrationRows(int start, int end) {
    isikReg.getRegistrationTableView().getSelectionModel().selectRange(start, end);
  }

  public void assertTableContainsRow(Object... cells) {
    TableView<?> tableView = robot.lookup(instanceOf(TableView.class)).queryTableView();
    assertThat(tableView, TableViewMatchers.containsRow(cells));
  }

  public void populateTable(int limitRows) {
    populateTable(limitRows, .8, .65, Duration.of(30 * 3, ChronoUnit.DAYS));
  }

  public void populateTable(int limitRows, double registeredChance, double chanceMultiplier, Duration registeredDateRange) {
    ZonedDateTime endDate = ZonedDateTime.now().minusDays(1);
    ZonedDateTime startDate = endDate.minus(registeredDateRange);
    long startSeconds = startDate.toEpochSecond();
    long endSeconds = endDate.toEpochSecond();
    long gapSeconds = endSeconds - startSeconds;

    registeredChance = Math.min(1, registeredChance);
    registeredChance = Math.max(0, registeredChance);

    chanceMultiplier = Math.min(1, chanceMultiplier);
    chanceMultiplier = Math.max(0, chanceMultiplier);

    int count = 0;
    while (count < limitRows) {
      Person person = newPerson(randomNumericString(8) + populateCounter++);
      person.setLastName(randomAlphabeticString(8) + populateCounter);
      person.setFirstName(randomAlphabeticString(8) + populateCounter);
      if (limitRows <= (count + person.getRegistrations().size())) break;
      double chance = registeredChance;
      while (rand.nextDouble() < chance) {
        chance *= chanceMultiplier;
        Registration reg = person.getOrNewNextRegistration();
        Instant dateInstant = Instant.ofEpochSecond(startSeconds + (rand.nextLong() % gapSeconds));
        reg.setRegisteredNoConfirm(new Date(dateInstant));
        if (limitRows <= (count + person.getRegistrations().size())) break;
      }
      count += person.getRegistrations().size();
    }
  }


  private String randomAlphabeticString(int len) {
    return rand.ints(65, 123)
        .filter(i -> (i <= 90 || i >= 97)).limit(len)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }

  private String randomNumericString(int len) {
    return rand.ints(48, 58).limit(len)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }


  // ########################## Click helpers ############################

  public void clickMenu(String labelRegex) {
    robot.clickOn(equalTo(robot.lookup(instanceOf(MenuBar.class)).lookup(LabeledMatchers.hasText(matchesPattern(labelRegex))).query()));
  }

  public void clickMenuItem(String labelRegex) {
    robot.clickOn(equalTo(robot.lookup(instanceOf(ContextMenuContent.MenuItemContainer.class)).lookup(LabeledMatchers.hasText(matchesPattern(labelRegex))).query()));
  }

  public void clickButton(String buttonLabelRegex) {
    clickButton(buttonLabelRegex, null);
  }

  public void clickButton(String buttonLabelRegex, String windowTitleRegex) {
    Parent windowNode = windowTitleRegex == null ? robot.listWindows().get(0).getScene().getRoot() : window(windowTitleRegex);
    NodeQuery query = robot.from(windowNode)
        .lookup(instanceOf(Button.class)).lookup(LabeledMatchers.hasText(matchesPattern(buttonLabelRegex)));

    waitForClickOn(equalTo(query.query()));
  }

  // ################################# SmartCard helpers #############################

  public void insertCard(FakeTerminalSimulator.FakeCard card) throws CardException {
    CardTerminal terminal = terminalSimulator.getTerminal();
    card.insert(terminal);
  }

  public void removeCard(FakeTerminalSimulator.FakeCard card) {
    card.eject();
  }

  public void assertStatusText(String subString) throws ExecutionException, InterruptedException {
    assertStatusText(subString, null);
  }

  public void assertStatusText(String subString, String windowTitleRegex) throws ExecutionException, InterruptedException {
    Parent windowNode = windowTitleRegex == null ? robot.listWindows().get(0).getScene().getRoot() : window(windowTitleRegex);

    waitForAssertThat(waitForQuery(robot.lookup(equalTo(windowNode))
            .lookup(".card-status-pane")
            .lookup(instanceOf(Labeled.class))),
        LabeledMatchers.hasText(containsString(subString)));
  }


  // ################################# Window helpers #############################

  public Parent waitForWindowShowing(String title) {
    try {
      WaitForAsyncUtils.waitFor(timeout, TimeUnit.NANOSECONDS, () -> {
        try {
          robot.window(title);
          return true;
        } catch (NoSuchElementException e) {
          return false;
        }
      });
    } catch (TimeoutException e) {
      fail("Window '" + title + "' dialog didn't show! Current windows: " +
          getWindowTitles().stream().map(t -> "'" + t + "'").collect(Collectors.toList()));
    }
    waitForFxEvents();
    return robot.window(title).getScene().getRoot();
  }

  public void assertNoDialogWindows() {
    assertEquals(1, getWindowTitles().size(), String.format("Expected only main window but found %s", getWindowTitles()));
  }

  public Parent window(String stageTitleRegex) {
    return robot.window(stageTitleRegex).getScene().getRoot();
  }

  private Set<String> getWindowTitles() {
    return robot.listWindows().stream()
        .filter(w -> w instanceof Stage)
        .map(s -> ((Stage) s).getTitle()).collect(Collectors.toSet());
  }


  // ################################# Robot waitFor helpers #############################

  private <T> void waitForClickOn(Matcher<Node> matcher) {
    try {
      WaitForAsyncUtils.waitFor(timeout, TimeUnit.NANOSECONDS, () -> {
        try {
          robot.clickOn(matcher);
          return true;
        } catch (FxRobotException e) {
          return false;
        }
      });
    } catch (TimeoutException e) {
      robot.clickOn(matcher);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T waitForQuery(NodeQuery query) throws ExecutionException, InterruptedException {
    CompletableFuture<T> result = new CompletableFuture<>();
    try {
      WaitForAsyncUtils.waitFor(timeout, TimeUnit.NANOSECONDS, () -> {
        try {
          result.complete((T) query.query());
          return true;
        } catch (EmptyNodeQueryException e) {
          return false;
        }
      });
    } catch (TimeoutException e) {
      result.complete((T) query.query());
    }
    return result.get();
  }

  private <T> void waitForAssertThat(T actual, Matcher<T> matcher) {
    try {
      WaitForAsyncUtils.waitFor(timeout, TimeUnit.NANOSECONDS, () -> matcher.matches(actual));
    } catch (TimeoutException e) {
      assertThat(actual, matcher);
    }
    waitForFxEvents();
  }


  // ################################### Debugging private methods ##############################

  private void printAncestors(Node node) {
    printAncestors(node, 0);
  }

  private void printAncestors(Node node, int tab) {
    printNode(node, tab);
    if (node.getParent() != null)
      printAncestors(node.getParent(), tab + 1);
  }

  private void printNode(Node node, int tab) {
    System.out.println(StringUtils.repeat('\t', tab) + "-> " + node);
  }


  // ########################################## Static Scene helpers ########################################

  public static boolean parentsSameStructure(Parent p1, Parent p2) {
    if (!p1.getClass().equals(p2.getClass())) return false;
    ObservableList<Node> c1 = p1.getChildrenUnmodifiable();
    ObservableList<Node> c2 = p2.getChildrenUnmodifiable();
    if (c1.size() != c2.size()) return false;
    for (int i = 0; i < c1.size(); i++) {
      Node n1 = c1.get(i);
      Node n2 = c2.get(i);
      if (!n1.getClass().equals(n2.getClass())) return false;
      if (n1 instanceof Labeled) {
        if (!((Labeled) n1).getText().equals(((Labeled) n2).getText())) return false;
      }
      if (n1 instanceof Parent) {
        if (!parentsSameStructure((Parent) n1, (Parent) n2)) return false;
      }
    }
    return true;
  }


  // ########################################## Static threading helpers ########################################

  public void waitForRunOnFxThread(Callable<Boolean> callable) throws TimeoutException {
    WaitForAsyncUtils.waitFor(timeout, TimeUnit.NANOSECONDS, () -> waitForResultRunOnFxThread(callable));
  }

  public static boolean waitForResultRunOnFxThread(Callable<Boolean> callable) throws ExecutionException, InterruptedException {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    Platform.runLater(() -> {
      try {
        future.complete(callable.call());
      } catch (Throwable e) {
        future.completeExceptionally(e);
      }
    });
    return future.get();
  }

  public static void waitForFxEvents() {
    waitForFxEvents(1);
  }

  public static void waitForFxEvents(int attemptsCount) {
    for (int attempt = 0; attempt < attemptsCount; attempt++) {
      blockFxThreadWithSemaphore();
    }
  }

  private static void blockFxThreadWithSemaphore() {
    Semaphore semaphore = new Semaphore(0);
    runOnFxThread(semaphore::release);
    try {
      semaphore.acquire();
    } catch (InterruptedException ignore) {
    }
  }

  private static void runOnFxThread(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }


}
