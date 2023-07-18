package com.knemerzitski.isikreg;

import com.google.common.jimfs.Jimfs;
import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.field.ObjFieldValueSet;
import com.knemerzitski.isikreg.person.Person;
import com.knemerzitski.isikreg.person.Registration;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.smartcard.FakeCards;
import com.knemerzitski.isikreg.smartcard.FakeTerminalSimulator;
import com.knemerzitski.isikreg.utils.PersonListTestUtils;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;
import org.testfx.service.query.EmptyNodeQueryException;

import javax.smartcardio.CardException;
import javax.smartcardio.TerminalFactory;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
public class IsikRegTest {

  private Stage primaryStage;
  private IsikReg isikReg;
  private FileSystem fileSystem;
  private Settings settings;
  private FakeTerminalSimulator terminalSimulator;
  private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
  private ConcurrentLinkedQueue<Throwable> uncaughtExceptions;

  @Start
  public void init(Stage primaryStage) throws NoSuchAlgorithmException {
    this.primaryStage = primaryStage;
    uncaughtExceptions = new ConcurrentLinkedQueue<>();
    terminalSimulator = new FakeTerminalSimulator(3);

    fileSystem = Jimfs.newFileSystem();
    settings = Settings.newDefault(fileSystem.getPath("./settings.json"));

    settings.smartCard.waitBeforeReadingCard = 0; // Don't need to wait with fake terminals
    settings.smartCard.waitForChangeLoopInterval = 1; // Must be positive with fake terminals, or will stay stuck waiting
    settings.smartCard.showSuccessStatusDuration = 10;
    settings.general.saveDelay = 1;

    uncaughtExceptionHandler = (t, e) -> {
      uncaughtExceptions.offer(e);
    };

    isikReg = new IsikReg(fileSystem, settings, uncaughtExceptionHandler) {

      @Override
      protected TerminalFactory createTerminalFactory() {
        return terminalSimulator.getTerminalFactory();
      }

    };
  }

  private void start() throws ExecutionException, InterruptedException {
    Actions.waitForResultRunOnFxThread(() -> {
      isikReg.start(primaryStage);
      return true;
    });
    Actions.waitForFxEvents(5);
  }

  @BeforeEach
  @AfterEach
  public void assertNoExceptions() {
    Throwable t;
    while ((t = uncaughtExceptions.poll()) != null) {
      fail(t);
    }
  }

  @AfterEach
  public void assertPersonListSaved() throws IOException, InterruptedException {
    assertTrue(isikReg.getPersonList().verifyWritten(), "Saved file is different from in memory state!");
  }

  @Stop
  public void stop() {
    isikReg.stop();
  }

  // ############################################# Basic ######################################

  @Test
  public void testNewRegistrationInMenuAndBottomRightAreSame(FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);


    String windowTitle = "Uus registreerimine";
    String cancelButton = "Tühista";

    actions.clickNewRegistrationButton();

    Parent parent1 = actions.waitForWindowShowing(windowTitle);
    actions.clickButton(cancelButton, windowTitle);

    actions.clickListMenu();
    actions.clickNewRegistrationMenuItem();

    Parent parent2 = actions.waitForWindowShowing(windowTitle);
    actions.clickButton(cancelButton, windowTitle);

    Assertions.assertTrue(Actions.parentsSameStructure(parent1, parent2), String.format("Window '%s' from bottom right button and list menu don't match", windowTitle));
  }

  public static Stream<ObjFieldValueSet> settingsInsertPersonProvider() {
    return ObjFieldValueSet.cartesianProduct(Settings.class, Stream.of(
        "general.insertPerson"
    ));
  }

  @ParameterizedTest
  @MethodSource("settingsInsertPersonProvider")
  public void testNewRegistrationNewPerson(ObjFieldValueSet conf, FxRobot robot) throws IllegalAccessException, ExecutionException, InterruptedException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(10);

    String windowTitle = "Uus registreerimine";

    actions.clickNewRegistrationButton();

    actions.waitForWindowShowing(windowTitle);

    String registrationType = "Välja";
    String personalCode = "kood";
    String lastName = "perenimi";
    String firstName = "eesnimi";

    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
    actions.clickButton("Registreeri", windowTitle);

    if (!settings.general.insertPerson) {
      actions.assertWarningDialog(
          "Uue isiku lisamine ei ole lubatud!",
          personalCode + " " + lastName + " " + firstName
      );
      return;
    }


    actions.assertNewPersonJustRegistered(registrationType, personalCode, lastName, firstName);
  }

  @Test
  public void testNewPersonRegistrationCancel(FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(10);

    String windowTitle = "Uus registreerimine";

    actions.clickNewRegistrationButton();

    actions.waitForWindowShowing(windowTitle);

    String personalCode = "kood";

    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode
    );
    actions.clickButton("Tühista", windowTitle);

    actions.assertPersonNotRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
  }

  @Test
  public void testNewRegistrationNewPersonPersonalCodeRequired(FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(10);

    String windowTitle = "Uus registreerimine";

    actions.clickNewRegistrationButton();

    actions.waitForWindowShowing(windowTitle);

    String registrationType = "Välja";
    String personalCode = "kood";

    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType);
    actions.clickButton("Registreeri", windowTitle);
    actions.waitForWindowShowing(windowTitle); // Window still here

    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode
    );
    actions.clickButton("Registreeri", windowTitle);

    actions.assertNewPersonJustRegistered(registrationType, personalCode, "", "");
  }

  @ParameterizedTest
  @MethodSource("settingsInsertPersonProvider")
  public void testNewRegistrationPersonNotRegistered(ObjFieldValueSet conf, FxRobot robot) throws ExecutionException, InterruptedException, IllegalAccessException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(10);

    String windowTitle = "Uus registreerimine";

    actions.clickNewRegistrationButton();

    actions.waitForWindowShowing(windowTitle);

    String registrationType = "Välja";
    String personalCode = "kood";
    String lastName = "perenimi";
    String firstName = "eesnimi";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
    actions.clickButton("Registreeri", windowTitle);

    actions.assertExistingPersonJustRegistered(existingPerson, registrationType);
  }

  @Test
  public void testNewRegistrationPersonRegistered(FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(10);

    String windowTitle = "Uus registreerimine";

    actions.clickNewRegistrationButton();

    actions.waitForWindowShowing(windowTitle);

    String registrationType = "Välja";
    String personalCode = "kood";
    String lastName = "perenimi";
    String firstName = "eesnimi";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewNextRegistration();
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusDays(1)));

    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
    actions.clickButton("Registreeri", windowTitle);


    actions.assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
    Registration reg = actions.getSelectedTableRegistration();
    actions.assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), personalCode, lastName, firstName);

    assertEquals(existingPerson, reg.getPerson());

    assertEquals(2, reg.getPerson().getRegistrations().size());
    assertNotEquals(existingRegistration, reg);
  }

  // ############################################# Registered counts / statistics ######################################

  @Test
  public void testRegisteredCounts(FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 565461354);

    actions.populateTable(50);
    List<Person> personList = isikReg.getPersonList().getUnmodifiableList();

    int iterations = 3;
    int changesPerIteration = 40;
    for(int k = 0; k < iterations; k++){
      actions.populateTable(5);
      Random rand = actions.getRandom();
      for(int i = 0; i < changesPerIteration; i++){
        int index = rand.nextInt(personList.size());
        Person p = personList.get(index);

        if(rand.nextDouble() < .6){
          // register
          Registration r = p.getOrNewNextRegistration();
          r.setRegisteredNoConfirm(true);
        }else{
          if(p.getLatestRegisteredRegistration() != null && rand.nextDouble() < .5){
            // clear latest
            p.getLatestRegisteredRegistration().setRegisteredNoConfirm(false);
          }else{
            // clear random
            p.getRegistrations().get(rand.nextInt(p.getRegistrations().size())).setRegisteredNoConfirm(false);
          }
        }
      }

      Map<String,Integer> registeredCount = PersonListTestUtils.getRegisteredCountsByType(isikReg.getPersonList());
      actions.assertRegisteredCount(registeredCount.values().stream());
    }
  }


  // ################################################ Menu "Nimekiri" ##############################################

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testClearAllRegistrations(Boolean confirmYes, FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(100);

    actions.clickListMenu();
    actions.clickClearAllRegistrationsMenuItem();

    ObservableList<Person> registrationList = isikReg.getPersonList().getUnmodifiableList();
    long registeredCount = !confirmYes ? registrationList.stream().filter(p -> p.getLatestRegisteredRegistration() != null).count() : 0;

    actions.assertConfirmDialog(confirmYes,
        "Oled kindel, et tahad registreerimised tühistada\\?",
        "Tühistan registreerimised\\?");

    assertEquals(registeredCount, registrationList.stream().filter(p -> p.getLatestRegisteredRegistration() != null).count());
    if (confirmYes) {
      actions.assertTableContainsRow(false, "Sisse", null);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testDeleteAll(Boolean confirmYes, FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(100);

    actions.clickListMenu();
    actions.clickDeleteAllPeopleRegistrationsMenuItem();

    long count = !confirmYes ? isikReg.getPersonList().size() : 0;

    actions.assertConfirmDialog(confirmYes,
        "Oled kindel, et tahad nimekirja ära kustutada\\?",
        "Kustutan nimekirja\\?");

    assertEquals(count, isikReg.getPersonList().size());
    assertEquals(confirmYes, isikReg.getRegistrationTableView().getItems().isEmpty());
  }

  @Test
  public void testDeleteAllDeleteNotAllowed(FxRobot robot) throws ExecutionException, InterruptedException {
    settings.general.deletePerson = false;
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(100);

    long count = isikReg.getPersonList().size();

    actions.clickListMenu();
    assertThrows(EmptyNodeQueryException.class, actions::clickDeleteAllPeopleRegistrationsMenuItem);

    actions.assertNoDialogWindows();

    assertEquals(count, isikReg.getPersonList().size());
  }


  // ################################################ Menu "Valitud read" ##############################################

  @Test
  public void testNewTypeRegistration(FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(100);

    Registration registration = isikReg.getRegistrationTableView().getItems().get(0);
    actions.selectRegistrationRow(registration);

    Person person = registration.getPerson();
    if (person.getLatestRegisteredRegistration() == null) {
      registration.setRegistered(true, true);
      registration.setRegisteredDate(new Date(ZonedDateTime.now().minusDays(1)));
    } else {
      registration = person.getLatestRegisteredRegistration();
    }

    String registrationType = person.getNextRegistrationType();

    actions.clickSelectedRowsMenu();
    actions.clickSelectedPersonNewTypeRegistrationMenuItem();
    String windowTitle = "Uut tüüpi registreerimine \\(Hetkel " +
        registration.getRegistrationType().toLowerCase() +
        " registreeritud\\)";
    actions.waitForWindowShowing(windowTitle);
    actions.assertFormValues(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), person.getPersonalCode(),
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), person.getLastName(),
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), person.getFirstName());
    actions.clickButton("Registreeri", windowTitle);

    actions.assertExistingPersonJustRegistered(person, registrationType);
    Registration newRegistration = actions.getSelectedTableRegistration();
    actions.assertRegistrationRegistered(person, newRegistration);
    assertNotEquals(newRegistration, registration);
  }

  public static Stream<ObjFieldValueSet> settingsUpdatePersonProvider() {
    return ObjFieldValueSet.cartesianProduct(Settings.class, Stream.of(
        "general.updatePerson"
    ));
  }

  @ParameterizedTest
  @MethodSource("settingsUpdatePersonProvider")
  public void testUpdateRegistrationOrPerson(ObjFieldValueSet conf, FxRobot robot) throws ExecutionException, InterruptedException, IllegalAccessException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(100);

    Registration registration = isikReg.getRegistrationTableView().getItems().get(0);
    actions.selectRegistrationRow(registration);
    registration.setRegistrationType("Sisse");
    registration.setRegisteredNoConfirm(true);

    Person person = registration.getPerson();

    actions.clickSelectedRowsMenu();
    actions.clickEditSelectPersonOrRegistrationMenuItem();

    String newRegistrationType = "Välja";
    String newLastName = "dsafsaf";

    String windowTitle = "Muuda (isikut/)?registreeringut " + registration.getRegisteredDate();
    actions.waitForWindowShowing(windowTitle);
    actions.assertFormValues(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registration.getRegistrationType(),
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), person.getPersonalCode(),
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), person.getLastName(),
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), person.getFirstName());
    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), newRegistrationType,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), newLastName);
    actions.clickButton("Muuda", windowTitle);


    if (settings.general.updatePerson) {
      assertEquals(newLastName, person.getLastName());
    } else {
      assertNotEquals(newLastName, person.getLastName());
    }
    assertEquals(newRegistrationType, registration.getRegistrationType());
  }

  @ParameterizedTest
  @MethodSource("settingsUpdatePersonProvider")
  public void testUpdatePersonalCode(ObjFieldValueSet conf, FxRobot robot) throws ExecutionException, InterruptedException, IllegalAccessException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(100);

    Registration registration = isikReg.getRegistrationTableView().getItems().get(0);
    actions.selectRegistrationRow(registration);
    Person person = registration.getPerson();

    actions.clickSelectedRowsMenu();
    if (!settings.general.updatePerson) {
      assertThrows(EmptyNodeQueryException.class, actions::clickEditSelectedPersonPersonalCodeMenuItem);
      actions.assertNoDialogWindows();
      return;
    }
    actions.clickEditSelectedPersonPersonalCodeMenuItem();

    String newPersonalCode = "changed";

    String windowTitle = "Muuda isikukoodi";
    actions.waitForWindowShowing(windowTitle);
    actions.assertFormValues(windowTitle,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), person.getPersonalCode());
    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), newPersonalCode);
    actions.clickButton("Muuda isikukoodi", windowTitle);

    assertEquals(newPersonalCode, person.getPersonalCode());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testClearSelectedRegistrations(Boolean confirmYes, FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(100);

    actions.selectRegistrationRows(0, 3);
    List<Registration> registrations = actions.getSelectedTableRegistrations();

    long count = !confirmYes ? registrations.stream().filter(Registration::isRegistered).count() : 0;

    actions.clickSelectedRowsMenu();
    actions.clickClearSelectedRegistrationsMenuItem();
    actions.assertConfirmCancelSelectedRegistrations(confirmYes, registrations);
    assertEquals(count, registrations.stream().filter(Registration::isRegistered).count());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testDeleteSelectedPeople(Boolean confirmYes, FxRobot robot) throws ExecutionException, InterruptedException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(100);

    actions.selectRegistrationRows(0, 3);
    List<Registration> registrations = actions.getSelectedTableRegistrations();
    Set<Person> personSet = registrations.stream().map(Registration::getPerson).collect(Collectors.toSet());

    actions.clickSelectedRowsMenu();
    actions.clickDeleteSelectedPeopleMenuItem();

    long count = isikReg.getPersonList().size() - (confirmYes ? personSet.size() : 0);

    actions.assertConfirmDeleteSelectedPeople(confirmYes, new ArrayList<>(personSet));

    assertEquals(count, isikReg.getPersonList().size());
  }

  @Test
  public void testDeleteSelectedPeopleDeleteNotAllowed(FxRobot robot) throws ExecutionException, InterruptedException {
    settings.general.deletePerson = false;
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(100);

    actions.selectRegistrationRows(0, 3);

    long count = isikReg.getPersonList().size();

    actions.clickSelectedRowsMenu();
    assertThrows(EmptyNodeQueryException.class, actions::clickDeleteSelectedPeopleMenuItem);

    actions.assertNoDialogWindows();

    assertEquals(count, isikReg.getPersonList().size());
  }


  // ############################################# Grace period and same type in row ######################################

  // Bottom right button

  public static Stream<ObjFieldValueSet> settingsGracePeriodProvider() {
    return ObjFieldValueSet.cartesianProduct(Settings.class, Stream.of(
        "general.registerDuringGracePeriod"
    ));
  }

  @ParameterizedTest
  @MethodSource("settingsGracePeriodProvider")
  public void testNewRegistrationPersonRegisteredDuringGracePeriod(ObjFieldValueSet conf, FxRobot robot) throws IllegalAccessException, ExecutionException, InterruptedException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(10);

    String windowTitle = "Uus registreerimine";

    actions.clickNewRegistrationButton();

    actions.waitForWindowShowing(windowTitle);

    String registrationType = "Välja";
    String personalCode = "kood";
    String lastName = "";
    String firstName = "";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewNextRegistration();
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusMinutes(1)));

    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
    actions.clickButton("Registreeri", windowTitle);

    actions.assertDuringGracePeriod(conf, existingPerson, existingRegistration, registrationType);
  }

  public static Stream<ObjFieldValueSet> settingsSameTypeInRowProvider() {
    return ObjFieldValueSet.cartesianProduct(Settings.class, Stream.of(
        "general.registerSameTypeInRow"
    ));
  }

  @ParameterizedTest
  @MethodSource("settingsSameTypeInRowProvider")
  public void testNewRegistrationPersonRegisteredSameTypeInRow(ObjFieldValueSet conf, FxRobot robot) throws IllegalAccessException, ExecutionException, InterruptedException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(10);

    String windowTitle = "Uus registreerimine";

    actions.clickNewRegistrationButton();

    actions.waitForWindowShowing(windowTitle);

    String registrationType = "Sisse";
    String personalCode = "kood";
    String lastName = "";
    String firstName = "";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewRegistration(registrationType);
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusMinutes(20)));

    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
    actions.clickButton("Registreeri", windowTitle);

    actions.assertSameTypeInRow(conf, existingPerson, existingRegistration, registrationType);
  }

  public static Stream<ObjFieldValueSet> settingsGracePeriodSameTypeInRowProvider() {
    return ObjFieldValueSet.cartesianProduct(Settings.class, Stream.of(
        "general.registerSameTypeInRow",
        "general.registerDuringGracePeriod"
    ));
  }

  @ParameterizedTest
  @MethodSource("settingsGracePeriodSameTypeInRowProvider")
  public void testNewRegistrationPersonRegisteredDuringGracePeriodSameTypeInRow(ObjFieldValueSet conf, FxRobot robot) throws IllegalAccessException, InterruptedException, ExecutionException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(10);

    String windowTitle = "Uus registreerimine";

    actions.clickNewRegistrationButton();

    actions.waitForWindowShowing(windowTitle);

    String registrationType = "Välja";
    String personalCode = "kood";
    String lastName = "";
    String firstName = "";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewRegistration(registrationType);
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusMinutes(1)));

    actions.fillForm(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
    actions.clickButton("Registreeri", windowTitle);

    actions.assertDuringGracePeriodSameTypeInRow(conf, existingPerson, existingRegistration, registrationType);
  }

  // Checkbox

  @Test
  public void testCheckBoxRegister(FxRobot robot) throws InterruptedException, ExecutionException, TimeoutException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(1);

    String registrationType = "Välja";
    String personalCode = "kood";
    String lastName = "perenimi";
    String firstName = "eesnimi";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewRegistration(registrationType);

    actions.selectRegistrationRow(existingRegistration);
    actions.clickTableRegisteredCheckBox(existingRegistration);

    actions.assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
    Registration reg = actions.getSelectedTableRegistration();
    actions.assertTableContainsRow(true, existingRegistration.getRegistrationType(), reg.getRegisteredDate(), personalCode, lastName, firstName);

    assertEquals(existingPerson, reg.getPerson());
    assertEquals(existingRegistration, reg);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testCheckBoxCancelRegistration(boolean confirmYes, FxRobot robot) throws ExecutionException, InterruptedException, TimeoutException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(1);

    String registrationType = "Välja";
    String personalCode = "kood";
    String lastName = "perenimi";
    String firstName = "eesnimi";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewRegistration(registrationType);
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusDays(1)));

    actions.selectRegistrationRow(existingRegistration);
    actions.clickTableRegisteredCheckBox(existingRegistration);

    actions.assertConfirmDialog(confirmYes,
        "Oled kindel, et tahad " + existingRegistration.getRegistrationType().toLowerCase() + " registreerimist tühistada\\?",
        existingRegistration.getRegistrationType() + " " + Actions.personDialogSubText(existingPerson));
    if (confirmYes) {
      assertFalse(existingRegistration.isRegistered());
      actions.assertTableContainsRow(false, registrationType, null, personalCode, lastName, firstName);
    } else {
      assertTrue(existingRegistration.isRegistered());
      actions.assertTableContainsRow(true, registrationType, existingRegistration.getRegisteredDate(), personalCode, lastName, firstName);
    }
  }

  @ParameterizedTest
  @MethodSource("settingsGracePeriodProvider")
  public void testCheckBoxRegisterPersonRegisteredDuringGracePeriod(ObjFieldValueSet conf, FxRobot robot) throws IllegalAccessException, ExecutionException, InterruptedException, TimeoutException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(1);

    String registrationType = "Välja";
    String personalCode = "kood";
    String lastName = "";
    String firstName = "";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewRegistration();
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusMinutes(1)));

    Registration emptyRegistration = existingPerson.getOrNewRegistration(registrationType);

    actions.selectRegistrationRow(emptyRegistration);
    actions.clickTableRegisteredCheckBox(emptyRegistration);

    actions.assertDuringGracePeriod(conf, existingPerson, existingRegistration, registrationType);
  }

  @ParameterizedTest
  @MethodSource("settingsSameTypeInRowProvider")
  public void testCheckBoxRegisterPersonRegisteredSameTypeInRow(ObjFieldValueSet conf, FxRobot robot) throws IllegalAccessException, ExecutionException, InterruptedException, TimeoutException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(1);

    String registrationType = "Sisse";
    String personalCode = "kood";
    String lastName = "";
    String firstName = "";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewRegistration(registrationType);
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusMinutes(20)));

    Registration emptyRegistration = existingPerson.getOrNewRegistration(registrationType);

    actions.selectRegistrationRow(emptyRegistration);
    actions.clickTableRegisteredCheckBox(emptyRegistration);

    actions.assertSameTypeInRow(conf, existingPerson, existingRegistration, registrationType);
  }

  @ParameterizedTest
  @MethodSource("settingsGracePeriodSameTypeInRowProvider")
  public void testCheckBoxRegisterPersonRegisteredDuringGracePeriodSameTypeInRow(ObjFieldValueSet conf, FxRobot robot) throws IllegalAccessException, ExecutionException, InterruptedException, TimeoutException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    actions.populateTable(1);

    String registrationType = "Välja";
    String personalCode = "kood";
    String lastName = "";
    String firstName = "";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewRegistration(registrationType);
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusMinutes(1)));

    Registration emptyRegistration = existingPerson.getOrNewRegistration(registrationType);

    actions.selectRegistrationRow(emptyRegistration);
    actions.clickTableRegisteredCheckBox(emptyRegistration);

    actions.assertDuringGracePeriodSameTypeInRow(conf, existingPerson, existingRegistration, registrationType);
  }


  // ##############################  Quick register buttons #################################

  @ParameterizedTest
  @MethodSource("settingsGracePeriodSameTypeInRowProvider")
  public void testQuickRegistrationButtons(ObjFieldValueSet conf, FxRobot robot) throws ExecutionException, InterruptedException, IllegalAccessException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    Person person = actions.newPerson("kood");
    person.setLastName("last");
    person.setFirstName("first");

    Registration registration = person.getOrNewRegistration();
    actions.selectRegistrationRow(registration);

    // Register "Sisse" 1
    actions.clickQuickRegisterButton("Sisse");
    actions.assertExistingPersonJustRegistered(person, "Sisse");

    // Clear "Sisse" 1
    actions.clickQuickCancelRegistrationButton();
    actions.assertConfirmCancelSelectedRegistration(true, registration);
    actions.assertPersonSelectedTableRowNotRegistered(person, "Sisse");

    // Register "Sisse" 1
    actions.clickQuickRegisterButton("Sisse");
    actions.assertExistingPersonJustRegistered(person, "Sisse");
    Registration in1 = actions.getSelectedTableRegistration();

    // Register "Välja" 1
    actions.clickQuickRegisterButton("Välja");

    // if grace period deny then button
    boolean registered = false;
    if (settings.general.registerDuringGracePeriod != Settings.Rule.DENY) {
      registered = actions.assertDuringGracePeriod(conf, person, in1, "Välja");
    } else {
      // Register button disabled due to grace period
      actions.assertNoDialogWindows();
      actions.assertRegistrationRegistered(person, in1);
    }
    if (registered) {
      Registration out1 = actions.getSelectedTableRegistration();

      // Register "Välja" 2
      settings.general.registerDuringGracePeriod = Settings.Rule.ALLOW; // Ignore grace period, so that same type in row dialogs are shown
      actions.clickQuickRegisterButton("Välja");

      if (settings.general.registerSameTypeInRow != Settings.Rule.DENY) {
        registered = actions.assertDuringGracePeriodSameTypeInRow(conf, person, out1, "Välja");
      } else {
        // Register button disabled due to same type in row
        actions.assertNoDialogWindows();
        actions.assertRegistrationRegistered(person, out1);
        registered = false;
      }
      if (registered) {
        Registration removedOut2 = actions.getSelectedTableRegistration();

        // Clear "Välja" 2
        actions.clickQuickCancelRegistrationButton();
        actions.assertConfirmCancelSelectedRegistration(true, removedOut2);
        actions.assertRegistrationRemoved(person, removedOut2);
      }

      // Clear "Välja" 1
      actions.selectRegistrationRow(out1);
      actions.clickQuickCancelRegistrationButton();
      actions.assertConfirmCancelSelectedRegistration(true, out1);
      actions.assertRegistrationRemoved(person, out1);
    }

    // Clear "Sisse" 1
    actions.selectRegistrationRow(in1);
    actions.clickQuickCancelRegistrationButton();
    actions.assertConfirmCancelSelectedRegistration(true, in1);
    actions.assertRegistrationExists(person, in1); // Last registration is reset...

    actions.assertTableContainsRow(false, "Sisse", null, person.getPersonalCode(), person.getLastName(), person.getFirstName());
  }

  @Test
  public void testQuickRegistrationCancelButtonDisable(FxRobot robot) throws ExecutionException, InterruptedException, IllegalAccessException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    Person person = actions.newPerson("kood");
    person.setLastName("last");
    person.setFirstName("first");

    Registration registration = person.getOrNewRegistration();
    actions.selectRegistrationRow(registration);

    // Register "Sisse" 1
    actions.clickQuickRegisterButton("Sisse");
    actions.assertExistingPersonJustRegistered(person, "Sisse");

    // Clear "Sisse" 1
    actions.clickQuickCancelRegistrationButton();
    actions.assertConfirmCancelSelectedRegistration(true, registration);
    actions.assertPersonSelectedTableRowNotRegistered(person, "Sisse");

    // Try to clear again
    actions.clickQuickCancelRegistrationButton();
    actions.assertNoDialogWindows();
    actions.assertPersonSelectedTableRowNotRegistered(person, "Sisse");
  }

  @Test
  public void testQuickRegistrationButtonEnabledAfterGracePeriod(FxRobot robot) throws ExecutionException, InterruptedException, IllegalAccessException {
    settings.general.registerDuringGracePeriod = Settings.Rule.DENY;
    settings.general.registerGracePeriod = 500;
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);

    Person person = actions.newPerson("kood");
    person.setLastName("last");
    person.setFirstName("first");

    Registration registration = person.getOrNewRegistration();
    actions.selectRegistrationRow(registration);

    // Register "Sisse"
    actions.clickQuickRegisterButton("Sisse");
    actions.assertExistingPersonJustRegistered(person, "Sisse");

    // Try to register during grace period
    actions.clickQuickRegisterButton("Sisse");
    actions.clickQuickRegisterButton("Välja");
    actions.assertNoDialogWindows();
    actions.assertRegistrationRegistered(person, registration);
    assertEquals(1, person.getRegistrations().size());

    // Wait for grace period to be over
    Thread.sleep(settings.general.registerGracePeriod / 2);
    actions.clickQuickRegisterButton("Välja");
    Registration out = actions.getSelectedTableRegistration();
    actions.assertRegistrationRegistered(person, out);
    actions.assertExistingPersonJustRegistered(person, "Välja");
  }


  // ############################################# Smartcard ######################################

  @ParameterizedTest
  @MethodSource("settingsInsertPersonProvider")
  public void testNewRegistrationNewPersonWithCard(ObjFieldValueSet conf, FxRobot robot) throws IllegalAccessException, ExecutionException, InterruptedException, CardException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Sisse";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    actions.assertStatusText("Sisesta ID-kaart");

    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    actions.insertCard(card);

    if (settings.general.insertPerson) {
      String windowTitle = "Uus registreerimine";
      actions.waitForWindowShowing(windowTitle);

      actions.assertFormValues(windowTitle,
          settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
          settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
          settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
          settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
      actions.clickButton("Registreeri", windowTitle);

      actions.assertStatusText(registrationType + " registreeritud!");

      actions.assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
      Registration reg = actions.getSelectedTableRegistration();
      actions.assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), personalCode, lastName, firstName);
    } else {
      actions.assertStatusText("Pole nimekirjas!");
    }

    card.eject();
    actions.assertStatusText("Sisesta ID-kaart");
  }

  @Test
  public void testNewRegistrationExistingPersonNotRegisteredWithCard(FxRobot robot) throws ExecutionException, InterruptedException, CardException, IllegalAccessException, IOException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Sisse";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    existingPerson.getOrNewNextRegistration();

    actions.assertStatusText("Sisesta ID-kaart");
    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    actions.insertCard(card);

    String windowTitle = "Uut tüüpi registreerimine";
    actions.waitForWindowShowing(windowTitle);

    actions.assertFormValues(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
    actions.clickButton("Registreeri", windowTitle);

    actions.assertStatusText(registrationType + " registreeritud!");

    actions.assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
    Registration reg = actions.getSelectedTableRegistration();
    actions.assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), personalCode, lastName, firstName);

    assertEquals(existingPerson, reg.getPerson());
  }

  @Test
  public void testNewRegistrationExistingPersonWithCardRegistered(FxRobot robot) throws ExecutionException, InterruptedException, CardException, IllegalAccessException {
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Välja";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewNextRegistration();
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusDays(1)));

    actions.assertStatusText("Sisesta ID-kaart");
    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    actions.insertCard(card);

    String windowTitle = "Uut tüüpi registreerimine \\(Hetkel " +
        existingRegistration.getRegistrationType().toLowerCase() +
        " registreeritud\\)";
    actions.waitForWindowShowing(windowTitle);

    actions.assertFormValues(windowTitle,
        settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
    actions.clickButton("Registreeri", windowTitle);

    actions.assertStatusText(registrationType + " registreeritud!");

    actions.assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
    Registration reg = actions.getSelectedTableRegistration();
    actions.assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), personalCode, lastName, firstName);

    assertEquals(existingPerson, reg.getPerson());

    assertEquals(2, reg.getPerson().getRegistrations().size());
    assertNotEquals(existingRegistration, reg);
  }

  public static Stream<ObjFieldValueSet> settingsRegisterExpiredCardsProvider() {
    return ObjFieldValueSet.cartesianProduct(Settings.class, Stream.of(
        "smartCard.registerExpiredCards"
    ));
  }

  @ParameterizedTest
  @MethodSource("settingsRegisterExpiredCardsProvider")
  public void testNewRegistrationExpiredCard(ObjFieldValueSet conf, FxRobot robot) throws CardException, IllegalAccessException, ExecutionException, InterruptedException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Sisse";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    actions.assertStatusText("Sisesta ID-kaart");

    FakeTerminalSimulator.FakeCard card = FakeCards.builder()
        .personalCode(personalCode)
        .lastName(lastName)
        .firstName(firstName)
        .expiryDateYear("2010")
        .build();
    actions.insertCard(card);

    boolean register = true;
    switch (settings.smartCard.registerExpiredCards) {
      case CONFIRM:
        boolean confirmYes = conf.getByName("smartCard.registerExpiredCards").getDataAsBoolean();
        actions.assertConfirmDialog(confirmYes, "ID-kaart on aegunud! Kas jätkan registreerimisega\\?");
        if (!confirmYes) register = false;
        break;
      case DENY:
        register = false;
        break;
    }

    if (register) {
      String windowTitle = "Uus registreerimine";
      actions.waitForWindowShowing(windowTitle);

      actions.assertFormValues(windowTitle,
          settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
          settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
          settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
          settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
      actions.clickButton("Registreeri", windowTitle);

      actions.assertStatusText(registrationType + " registreeritud!");

      actions.assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
      Registration reg = actions.getSelectedTableRegistration();
      actions.assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), personalCode, lastName, firstName);
    } else {
      actions.assertStatusText("ID-kaart on aegunud!");
    }

    card.eject();
    actions.assertStatusText("Sisesta ID-kaart");
  }

  public static Stream<ObjFieldValueSet> settingsRegisterPersonNotInListProvider() {
    return ObjFieldValueSet.cartesianProduct(Settings.class, Stream.of(
        "smartCard.registerPersonNotInList"
    ));
  }

  @ParameterizedTest
  @MethodSource("settingsRegisterPersonNotInListProvider")
  public void testNewRegistrationPersonNotInListCard(ObjFieldValueSet conf, FxRobot robot) throws CardException, IllegalAccessException, ExecutionException, InterruptedException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Sisse";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    actions.assertStatusText("Sisesta ID-kaart");

    FakeTerminalSimulator.FakeCard card = FakeCards.builder()
        .personalCode(personalCode)
        .lastName(lastName)
        .firstName(firstName)
        .build();
    actions.insertCard(card);

    boolean register = true;
    switch (settings.smartCard.registerPersonNotInList) {
      case CONFIRM:
        boolean confirmYes = conf.getByName("smartCard.registerPersonNotInList").getDataAsBoolean();
        actions.assertConfirmDialog(confirmYes, "ID-kaart pole nimekirjas\\. Kas jätkan registreerimisega\\?");
        if (!confirmYes) register = false;
        break;
      case DENY:
        register = false;
        break;
    }

    if (register) {
      String windowTitle = "Uus registreerimine";
      actions.waitForWindowShowing(windowTitle);

      actions.assertFormValues(windowTitle,
          settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
          settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
          settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
          settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
      actions.clickButton("Registreeri", windowTitle);

      actions.assertStatusText(registrationType + " registreeritud!");

      actions.assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
      Registration reg = actions.getSelectedTableRegistration();
      actions.assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), personalCode, lastName, firstName);
    } else {
      actions.assertStatusText("Pole nimekirjas!");
    }

    card.eject();
    actions.assertStatusText("Sisesta ID-kaart");
  }

  @ParameterizedTest
  @MethodSource("settingsInsertPersonProvider")
  public void testNewQuickRegistrationNewPersonWithCard(ObjFieldValueSet conf, FxRobot robot) throws IllegalAccessException, ExecutionException, InterruptedException, CardException {
    settings.smartCard.quickNewPersonRegistration = true;
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Sisse";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    actions.assertStatusText("Sisesta ID-kaart");

    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    actions.insertCard(card);

    if (settings.general.insertPerson) {
      actions.assertStatusText(registrationType + " registreeritud!");

      actions.assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
      Registration reg = actions.getSelectedTableRegistration();
      actions.assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), personalCode, lastName, firstName);
    } else {
      actions.assertStatusText("Pole nimekirjas!");
    }

    card.eject();
    actions.assertStatusText("Sisesta ID-kaart");
  }

  @Test
  public void testNewQuickRegistrationExistingPersonWithCard(FxRobot robot) throws ExecutionException, InterruptedException, CardException {
    settings.smartCard.quickExistingPersonRegistration = true;
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Välja";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewNextRegistration();
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusDays(1)));

    actions.assertStatusText("Sisesta ID-kaart");
    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    actions.insertCard(card);

    actions.assertStatusText(registrationType + " registreeritud!");

    actions.assertPersonRegisteredTimeAgo(personalCode, 1, ChronoUnit.SECONDS);
    Registration reg = actions.getSelectedTableRegistration();
    actions.assertTableContainsRow(true, registrationType, reg.getRegisteredDate(), personalCode, lastName, firstName);
    assertEquals(existingPerson, reg.getPerson());

    assertEquals(2, reg.getPerson().getRegistrations().size());
    assertNotEquals(existingRegistration, reg);

    card.eject();
    actions.assertStatusText("Sisesta ID-kaart");
  }

  @ParameterizedTest
  @MethodSource("settingsGracePeriodProvider")
  public void testNewRegistrationExistingPersonWithCardDuringGracePeriod(ObjFieldValueSet conf, FxRobot robot) throws ExecutionException, InterruptedException, CardException, IllegalAccessException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Välja";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewNextRegistration();
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusMinutes(1)));

    actions.assertStatusText("Sisesta ID-kaart");
    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    actions.insertCard(card);

    Settings.Rule register = actions.assertDuringGracePeriodContinueRegister(conf, existingRegistration);

    switch (register) {
      case ALLOW:
        String windowTitle = "Uut tüüpi registreerimine \\(Hetkel " +
            existingRegistration.getRegistrationType().toLowerCase() +
            " registreeritud\\)";
        actions.waitForWindowShowing(windowTitle);

        actions.assertFormValues(windowTitle,
            settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType,
            settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
            settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
            settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
        actions.clickButton("Registreeri", windowTitle);


        actions.assertStatusText(registrationType + " registreeritud!");
        break;
      case DENY:
        actions.assertWarningGracePeriod(existingRegistration);
      case CONFIRM:
        actions.assertStatusText("On juba " + existingRegistration.getRegistrationType().toLowerCase() + " registreeritud!");
    }

    actions.assertExistingPersonJustRegistration(register == Settings.Rule.ALLOW, existingPerson, existingRegistration, registrationType);
  }

  @ParameterizedTest
  @MethodSource("settingsSameTypeInRowProvider")
  public void testNewRegistrationExistingPersonWithCardSameTypeInRow(ObjFieldValueSet conf, FxRobot robot) throws ExecutionException, InterruptedException, CardException, IllegalAccessException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Välja";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewRegistration(registrationType);
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusDays(1)));

    actions.assertStatusText("Sisesta ID-kaart");
    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    actions.insertCard(card);

    String windowTitle = "Uut tüüpi registreerimine \\(Hetkel " +
        existingRegistration.getRegistrationType().toLowerCase() +
        " registreeritud\\)";
    actions.waitForWindowShowing(windowTitle);

    actions.assertFormValues(windowTitle,
        settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
        settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
        settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
    actions.fillForm(windowTitle, settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType);
    actions.clickButton("Registreeri", windowTitle);

    boolean register = actions.assertSameTypeInRowRegister(conf, existingRegistration, registrationType);
    actions.assertExistingPersonJustRegistration(register, existingPerson, existingRegistration, registrationType);

    if (register) {
      actions.assertStatusText(registrationType + " registreeritud!");
    } else {
      actions.assertStatusText("On juba " + existingRegistration.getRegistrationType().toLowerCase() + " registreeritud!");
    }
  }

  @ParameterizedTest
  @MethodSource("settingsGracePeriodSameTypeInRowProvider")
  public void testNewRegistrationExistingPersonWithCardDuringGracePeriodSameTypeInRow(ObjFieldValueSet conf, FxRobot robot) throws ExecutionException, InterruptedException, CardException, IllegalAccessException {
    conf.set(settings);
    start();

    Actions actions = new Actions(isikReg, terminalSimulator, robot, 4234235);
    actions.populateTable(10);

    String registrationType = "Välja";
    String personalCode = "34215133";
    String lastName = "PERENIMI";
    String firstName = "EESNIMI";

    Person existingPerson = actions.newPerson(personalCode);
    existingPerson.setLastName(lastName);
    existingPerson.setFirstName(firstName);

    Registration existingRegistration = existingPerson.getOrNewRegistration(registrationType);
    existingRegistration.setRegistered(true, true);
    existingRegistration.setRegisteredDate(new Date(ZonedDateTime.now().minusMinutes(1)));

    actions.assertStatusText("Sisesta ID-kaart");
    FakeTerminalSimulator.FakeCard card = FakeCards.createEstIdCardBasic(personalCode, lastName, firstName);
    actions.insertCard(card);

    // check grace period
    Settings.Rule gracePeriodOk = actions.assertDuringGracePeriodContinueRegister(conf, existingRegistration);
    boolean register = false;
    switch (gracePeriodOk) {
      case ALLOW:
        String windowTitle = "Uut tüüpi registreerimine \\(Hetkel " +
            existingRegistration.getRegistrationType().toLowerCase() +
            " registreeritud\\)";
        actions.waitForWindowShowing(windowTitle);

        actions.assertFormValues(windowTitle,
            settings.getColumn(Column.Id.PERSONAL_CODE).getLabel(), personalCode,
            settings.getColumn(Column.Id.LAST_NAME).getLabel(), lastName,
            settings.getColumn(Column.Id.FIRST_NAME).getLabel(), firstName);
        actions.fillForm(windowTitle, settings.getColumn(Column.Id.REGISTRATION_TYPE).getLabel(), registrationType);
        actions.clickButton("Registreeri", windowTitle);

        if (settings.general.registerDuringGracePeriod == Settings.Rule.CONFIRM) { // Already confirmed a dialog once
          register = actions.assertSameTypeInRowContinue(existingRegistration);
        } else {
          register = actions.assertSameTypeInRowRegister(conf, existingRegistration, registrationType);
        }
        break;
      case DENY:
        actions.assertWarningGracePeriod(existingRegistration);
    }

    if (register) {
      actions.assertStatusText(registrationType + " registreeritud!");
    } else {
      actions.assertStatusText("On juba " + existingRegistration.getRegistrationType().toLowerCase() + " registreeritud!");
    }

    actions.assertExistingPersonJustRegistration(register, existingPerson, existingRegistration, registrationType);
  }

}
