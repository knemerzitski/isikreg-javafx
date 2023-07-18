package com.knemerzitski.isikreg.person;

import com.google.gson.InstanceCreator;
import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.exception.AppQuitException;
import com.knemerzitski.isikreg.gson.GsonDateProperty;
import com.knemerzitski.isikreg.settings.ColumnProperties;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Person {

  public static class PersonInstanceCreator implements InstanceCreator<Person> {

    private final Settings settings;

    public PersonInstanceCreator(Settings settings) {
      this.settings = settings;
    }

    @Override
    public Person createInstance(Type type) {
      return new Person(settings, false);
    }
  }

  public static class Identity extends Person {

    private final String personalCode;

    public Identity(String personalCode) {
      super(null, false);
      this.personalCode = personalCode;
    }

    @Override
    public String getPersonalCode() {
      return personalCode;
    }
  }

  public class RegistrationCheckProcess {

    private final LocalDateTime now = LocalDateTime.now();
    private boolean gracePeriodConfirmed = false;

    public boolean checkGracePeriod() {
      return checkGracePeriod(null);
    }

    public boolean checkGracePeriod(String desiredRegistrationType) {
      if (settings.general.registerDuringGracePeriod == Settings.Rule.ALLOW) {
        return true;
      }
      Registration latestRegisteredRegistration = getLatestRegisteredRegistration();
      if (latestRegisteredRegistration == null)
        return true;
      Date regDate = latestRegisteredRegistration.getRegisteredDate();
      Duration graceDur = Duration.of(settings.general.registerGracePeriod, ChronoUnit.MILLIS);
      String regText = desiredRegistrationType != null ? "Kas registreerin " + desiredRegistrationType.toLowerCase() + "?" : "Kas jätkan registreerimisega?";
      LocalDateTime afterRegDate = now.minus(graceDur);
      if (afterRegDate.isAfter(regDate.getLocalDateTime())) {
        return true; // After grace period
      } else if (settings.general.registerDuringGracePeriod == Settings.Rule.CONFIRM) {
        boolean confirmed = settings.dialogHandler.confirm(
            "On " + Date.untilText1(regDate.getLocalDateTime(), now) + " tagasi " + latestRegisteredRegistration.getRegistrationType().toLowerCase() +
                " registreeritud.\n" + regText,
            getDisplayInfo());
        if (!this.gracePeriodConfirmed && confirmed) this.gracePeriodConfirmed = true;
        return confirmed;
      } else if (settings.general.registerDuringGracePeriod == Settings.Rule.DENY) {
        settings.dialogHandler.warning(
            "On " + latestRegisteredRegistration.getRegistrationType().toLowerCase() +
                " registreeritud!\nRegistreerida saab " + Date.untilText2(afterRegDate, regDate.getLocalDateTime()) + " pärast.",
            getDisplayInfo());
        return false;
      }
      return false;
    }

    public boolean sameTypeDeny(@NotNull String desiredType) {
      if (settings.general.registerSameTypeInRow == Settings.Rule.ALLOW) {
        return false;
      }

      Registration latestReg = getLatestRegisteredRegistration();
      if (latestReg == null)
        return false;

      String latestType = latestReg.getRegistrationType();

      if (!latestType.equals(desiredType))
        return false;

      return settings.general.registerSameTypeInRow == Settings.Rule.DENY;
    }

    public boolean checkSameTypeInRow(@NotNull String desiredType) {
      if (settings.general.registerSameTypeInRow == Settings.Rule.ALLOW) {
        return true;
      }

      Registration latestReg = getLatestRegisteredRegistration();
      if (latestReg == null)
        return true;

      String latestType = latestReg.getRegistrationType();

      if (!latestType.equals(desiredType))
        return true;

      if (settings.general.registerSameTypeInRow == Settings.Rule.CONFIRM) {
        return gracePeriodConfirmed || settings.dialogHandler.confirm(
            "On juba " + latestType.toLowerCase() + " registreeritud!\nKas registreerin veelkord " + desiredType.toLowerCase() + "?",
            getDisplayInfo());
      } else if (settings.general.registerSameTypeInRow == Settings.Rule.DENY) {
        settings.dialogHandler.warning(
            "On juba " + latestType.toLowerCase() + " registreeritud!\nJärjest topelt sama registreerimine ei ole lubatud.",
            getDisplayInfo());
        return false;
      }

      return false;
    }

  }

  final transient Settings settings;

  private ColumnProperties properties;
  private List<Registration> registrations;
  private transient ObservableList<Registration> observableRegistrations;
  private transient ListChangeListener<Registration> latestRegisteredRegistrationsListener;

  private transient boolean initialized = false;
  private transient boolean removed = false;

  private transient StringProperty personalCodeProperty;
  private transient StringProperty lastNameProperty;
  private transient StringProperty firstNameProperty;

  private transient ObjectProperty<Registration> latestRegisteredProperty;
  private transient ChangeListener<Registration> latestRegisteredPropertyListener;
  private transient ReadOnlyStringWrapper registeredTypeProperty;

  public Person(Settings settings) {
    this(settings, true);
  }

  public Person(Settings settings, boolean initialize) {
    this(settings, initialize, true);
  }

  public Person(Settings settings, boolean initialize, boolean createListeners) {
    this.settings = settings;
    if (initialize)
      init(createListeners, true);
  }

  public Person(Settings settings, @NotNull ColumnProperties properties) {
    this.settings = settings;
    this.properties = new ColumnProperties(settings);
    properties.forEach((column, property) -> {
      if (column.group == Column.Group.PERSON) {
        this.properties.put(column, property);
      }
    });
    Registration r = new Registration(settings, properties);
    boolean cleanUpRegs = true;
    if (!r.getProperties().isEmpty()) {
      registrations = newRegistrationList();
      registrations.add(r);
      cleanUpRegs = false;
    }

    init(true, cleanUpRegs);
  }

  public void init() {
    init(true, true);
  }

  public void init(boolean createListeners, boolean cleanUpRegistrations) {
    if (initialized) {
      throw new AppQuitException("Person has already been initialized");
    }

    if (properties == null)
      properties = new ColumnProperties(settings);

    settings.getColumnsByGroup(Column.Group.PERSON).forEach(column -> {
      if (properties.get(column) == null) {
        Property<?> property = settings.newProperty(column);
        if (property != null)
          properties.put(column, property);
      }
    });


    Property<?> property = properties.get(settings.getColumn(Column.Id.PERSONAL_CODE));
    if (property instanceof StringProperty) {
      personalCodeProperty = (StringProperty) property;
    } else {
      personalCodeProperty = null;
    }

    property = properties.get(settings.getColumn(Column.Id.LAST_NAME));
    if (property instanceof StringProperty) {
      lastNameProperty = (StringProperty) property;
    } else {
      lastNameProperty = null;
    }

    property = properties.get(settings.getColumn(Column.Id.FIRST_NAME));
    if (property instanceof StringProperty) {
      firstNameProperty = (StringProperty) property;
    } else {
      firstNameProperty = null;
    }

    if (registrations == null)
      registrations = newRegistrationList();
    registrations.forEach(r -> r.init(this));

    observableRegistrations = FXCollections.observableList(registrations);

    latestRegisteredProperty = new SimpleObjectProperty<>();

    if (createListeners) {
      // Make sure all values are observed
      List<Registration> tmpRegistrations = new ArrayList<>(registrations);
      registrations.clear();

      // Bind latest registration property
      Map<ObjectProperty<Date>, ChangeListener<Date>> dateListeners = new IdentityHashMap<>();

      latestRegisteredRegistrationsListener = c -> {
        while (c.next()) {
          c.getAddedSubList().forEach(newR -> {
            ChangeListener<Date> listener = (observable, oldDate, newDate) -> {
//              System.out.println("Registration date changed " + newDate);
              Registration curR = latestRegisteredProperty.get();
              if (curR == newR) {
                Optional<Registration> findR = newR.getPerson().getRegistrations().stream().filter(r -> r != curR && r.getRegisteredDate() != null).max((o1, o2) -> {
                  Date d1 = o1.getRegisteredDate();
                  Date d2 = o2.getRegisteredDate();
                  int compare = d1.compareTo(d2);
                  if (compare == 0 && settings.general.warnDuplicateRegistrationDate) {
                    runLaterSafe(() -> warnSameRegistrationDate(d1));
                  }
                  return compare;
                });
                if (findR.isPresent()) {
                  latestRegisteredProperty.set(findR.get());
                } else {
                  latestRegisteredProperty.set(null);
                }
              } else {
                // clicked on different registration
                Date curDate = curR != null ? curR.getRegisteredDate() : null;
//                System.out.println("Compare " + curDate + " " + newDate);
                if (newDate != null && curDate != null) {
                  int compare = newDate.compareTo(curDate);
                  if (compare >= 1) {
                    latestRegisteredProperty.set(newR);
                  } else if (compare == 0 && settings.general.warnDuplicateRegistrationDate) {
                    runLaterSafe(() -> {
                      warnSameRegistrationDate(newDate);
                    });
                  }
                } else if (newDate != null) {
                  latestRegisteredProperty.set(newR);
                } else if (curDate == null) {
                  latestRegisteredProperty.set(null);
                }
              }

              // Check if there already is same registration if so then remove it
              for (Registration r : getRegistrations()) {
                if (r == newR)
                  continue;
                if (r.equals(newR)) { // Remove identical registrations
                  runLaterSafe(() -> getRegistrations().remove(newR));
                  break;
                }
              }
            };
            GsonDateProperty prop = newR.registeredDateProperty();
            // also call listener????
            dateListeners.put(prop, listener);
            prop.addListener(listener);
            if (prop.get() != null)
              listener.changed(prop, null, prop.get());
          });
          c.getRemoved().forEach(remR -> {
            if (latestRegisteredProperty.get() == remR) {
              latestRegisteredProperty.set(null);
            }
            ObjectProperty<Date> prop = remR.registeredDateProperty();
            if (prop == null)
              return;
            ChangeListener<Date> listener = dateListeners.get(prop);
            if (listener == null)
              return;
            prop.removeListener(listener);
            dateListeners.remove(prop);
          });
        }
      };
      observableRegistrations.addListener(latestRegisteredRegistrationsListener);

      registeredTypeProperty = new ReadOnlyStringWrapper(null);
      latestRegisteredPropertyListener = (observable, oldValue, reg) -> {
        if (reg == null) {
          registeredTypeProperty.unbind();
          registeredTypeProperty.set(null);
        } else {
          registeredTypeProperty.bind(reg.registrationTypeProperty());
        }
      };
      latestRegisteredProperty.addListener(latestRegisteredPropertyListener);

      observableRegistrations.addAll(tmpRegistrations);
    }

    if (cleanUpRegistrations)
      cleanUpRegistrations();

    initialized = true;
  }

  private List<Registration> newRegistrationList() {
    return new CopyOnWriteArrayList<>();
  }

  private void warnSameRegistrationDate(Date date) {
    settings.dialogHandler.warning("Sama registreerimise aeg " + date.toIsoLocalString() + "!", getDisplayInfo());
  }

  public void removeRegistrations() {
    ArrayList<Registration> cpyRegistrations = new ArrayList<>(registrations);
    cpyRegistrations.forEach(Registration::remove);
  }

  //  public void merge(Person newPerson, boolean replaceRegistrations) {
  public void merge(Person newPerson) {
    properties.merge(newPerson.getProperties());

    newPerson.getRegistrations().forEach(this::merge);

    // Merge removes newPerson
    newPerson.removeRegistrations();
    newPerson.removed();
  }

  /**
   * Person is added or updated in the list.
   * Properties might have changed and should be checked.
   */
  public void updateAutoFillIndex() {
    properties.updateFormAutoFillIndex();
    registrations.forEach(r -> r.getProperties().updateFormAutoFillIndex());
  }

  /**
   * Removed from the list.
   */
  public void removed() {
    if (!initialized || removed)
      return;
    removed = true;
    if (observableRegistrations != null && latestRegisteredRegistrationsListener != null)
      observableRegistrations.removeListener(latestRegisteredRegistrationsListener);
    latestRegisteredRegistrationsListener = null;
    if (latestRegisteredProperty != null && latestRegisteredPropertyListener != null)
      latestRegisteredProperty.removeListener(latestRegisteredPropertyListener);
    registeredTypeProperty.unbind();
    registeredTypeProperty = null;
    latestRegisteredProperty = null;
    latestRegisteredPropertyListener = null;
    properties = null;
    registrations = null;
    observableRegistrations = null;
    personalCodeProperty = null;
    lastNameProperty = null;
    firstNameProperty = null;
  }

  public RegistrationCheckProcess newRegistrationCheckProcess() {
    return new RegistrationCheckProcess();
  }

  public boolean checkRegistrationGracePeriod() {
    return checkRegistrationGracePeriod(null);
  }

  public boolean checkRegistrationGracePeriod(String desiredRegistrationType) {
    return new RegistrationCheckProcess().checkGracePeriod(desiredRegistrationType);
  }

  public boolean checkRegistrationSameTypeInRow(@NotNull String desiredType) {
    return new RegistrationCheckProcess().checkSameTypeInRow(desiredType);
  }

  public String getDisplayInfo() {
    String type = getRegisteredType();
    return (type != null ? type + " " : "") + properties.getPersonDisplayInfo();
  }

  private void showAlreadyRegisteredWarning(@NotNull String registrationType) {
    settings.dialogHandler.warning("On juba " + registrationType.toLowerCase() + " registreeritud!",
        getDisplayInfo());
  }

  public void showNewPersonNotAllowedWarning() {
    settings.dialogHandler.warning("Uue isiku lisamine ei ole lubatud!",
        getDisplayInfo());
  }

  public boolean hasEmptyRegistration() {
    return !getRegistrations().stream().allMatch(Registration::isRegistered);
  }

  public boolean hasEmptyRegistration(@NotNull String registrationType) {
    return getRegistrations().stream().anyMatch(r -> r.getRegistrationType().equals(registrationType) && !r.isRegistered());
  }

  public ColumnProperties getProperties() {
    return properties;
  }

  public StringProperty personalCodeProperty() {
    return personalCodeProperty;
  }

  public String getPersonalCode() {
    if (personalCodeProperty == null || personalCodeProperty.get() == null)
      return "";
    return personalCodeProperty.get();
  }

  public void setPersonalCode(String personalCode) {
    if (personalCodeProperty != null)
      personalCodeProperty.set(personalCode);
  }

  public String getLastName() {
    if (lastNameProperty == null || lastNameProperty.get() == null)
      return "";
    return lastNameProperty.get();
  }

  public void setLastName(String lastName) {
    if (lastNameProperty != null)
      lastNameProperty.set(lastName);
  }

  public String getFirstName() {
    if (firstNameProperty == null || firstNameProperty.get() == null)
      return "";
    return firstNameProperty.get();
  }

  public void setFirstName(String firstName) {
    if (firstNameProperty != null)
      firstNameProperty.set(firstName);
  }

  Registration newRegistration() {
    Registration reg = new Registration(this);
    getRegistrations().add(reg);
    return reg;
  }

  public String getNextRegistrationType() {
    List<String> options = settings.getRegistrationTypes();

    Registration latestRegisteredRegistration = getLatestRegisteredRegistration();
    if (latestRegisteredRegistration != null) {
      String curType = latestRegisteredRegistration.getRegistrationType();
      int index = options.indexOf(curType);
      if (index != -1) {
        return options.get((index + 1) % options.size());
      } else if (!options.isEmpty()) {
        return options.get(0);
      }
    } else {
      if (!options.isEmpty()) {
        return options.get(0);
      }
    }
    return null;
//    }
//    return null;
  }

  public Registration getOrNewNextRegistration() {
    return getOrNewRegistration(getNextRegistrationType());
  }

  public Registration getOrNewRegistration() {
    return getOrNewRegistration(null);
  }

  public Registration getOrNewRegistration(String desiredType) {
    // Use next registration type
    if (desiredType != null) {
      // Find unused existing registration, if type is different, then change it
      //Registration reg = registrations.stream().filter(r -> r.getRegisteredDate() == null && r.getRegistrationType().equals(desiredType)).findFirst().orElse(null);

      Registration regWithType = registrations.stream()
          .filter(r -> r.getRegisteredDate() == null && r.getRegistrationType().equals(desiredType))
          .findFirst().orElse(null);
      if (regWithType != null)
        return regWithType;

      Registration regRandomType = registrations.stream().filter(r -> r.getRegisteredDate() == null).findFirst().orElse(null);
      if (regRandomType != null) {
        regRandomType.setRegistrationType(desiredType);
        return regRandomType;
      }

      Registration reg = newRegistration();
      reg.setRegistrationType(desiredType);
      return reg;
    }

    Registration emptyReg = getRegistrations().stream().filter(r -> r.getRegisteredDate() == null).findFirst().orElse(null);
    if (emptyReg != null)
      return emptyReg;

    Registration reg = newRegistration();
    reg.setDefaultRegistrationType();
    return reg;
  }

  private Registration merge(Registration registration) {
    Optional<Registration> optionalSameReg = getRegistrations().stream().filter(r -> r.same(registration)).findFirst();
    if (optionalSameReg.isPresent()) {
      Registration sameRegistration = optionalSameReg.get();
      sameRegistration.merge(registration.getProperties());
      return sameRegistration;
    } else {
      Registration r = new Registration(this, registration);
      getRegistrations().add(r);
      return r;
    }
  }

  public void cleanUpRegistrations() {
    getRegistrations().removeIf(r -> r.getRegisteredDate() == null);
    if (registrations.isEmpty()) {
      Registration r = newRegistration();
      r.setDefaultRegistrationType();
    }
    getRegistrations().forEach(r -> r.setRegistered(r.getRegisteredDate() != null, true));
  }

  public ObservableList<Registration> getRegistrations() {
    return observableRegistrations;
  }

  public Registration getLatestRegistration(Registration skipThis) {
    if (registrations.isEmpty())
      return null;
    for (int i = getRegistrations().size() - 1; i >= 0; i--) {
      Registration r = getRegistrations().get(i);
      if (r != skipThis)
        return r;
    }
    return null;
  }

  public Registration getLatestRegistration() {
    if (registrations.isEmpty())
      return null;
    return getRegistrations().get(getRegistrations().size() - 1);
  }

  public Registration getLatestRegisteredRegistration() {
    return latestRegisteredProperty.get();
  }

  public ReadOnlyStringProperty registeredTypeProperty() {
    return registeredTypeProperty.getReadOnlyProperty();
  }

  public String getRegisteredType() {
    return registeredTypeProperty.get();
  }

  private void runLaterSafe(Runnable run) {
    Platform.runLater(() -> {
      if (removed) return;
      run.run();
    });
  }

  public void setEmptyStringPropertiesToNull() {
    properties.setEmptyStringsToNull();
    registrations.forEach(r -> r.getProperties().setEmptyStringsToNull());
  }

  public boolean equals(Person p) {
    if (p == null) return false;
    if (!properties.equals(p.properties)) return false;

    List<Registration> remaining = new ArrayList<>(p.registrations);
    for (Registration r : registrations) {
      boolean found = false;
      Iterator<Registration> itr = remaining.iterator();
      while (itr.hasNext()) {
        Registration r2 = itr.next();
        if (r.getProperties().equals(r2.getProperties())) {
          itr.remove();
          found = true;
          break;
        }
      }
      if (!found) return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return properties + " " + registrations;
  }
}
