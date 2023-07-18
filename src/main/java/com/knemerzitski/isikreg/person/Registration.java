package com.knemerzitski.isikreg.person;

import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.exception.AppQuitException;
import com.knemerzitski.isikreg.gson.GsonBooleanProperty;
import com.knemerzitski.isikreg.gson.GsonDateProperty;
import com.knemerzitski.isikreg.settings.ColumnProperties;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class Registration {

  private transient Settings settings;

  private ColumnProperties properties;

  private transient ColumnProperties allProperties;

  private transient boolean initialized = false;
  private transient boolean removed = false;

  private transient Person person;

  private transient StringProperty registrationTypeProperty;

  private transient BooleanProperty registeredProperty;
  private transient BooleanProperty registerSilentProperty;
  private transient ChangeListener<Boolean> registerSilentPropertyListener;
  private transient GsonDateProperty registeredDateProperty;


  public Registration() {
  }

  public Registration(Settings settings, @NotNull ColumnProperties properties) {
    this.settings = settings;
    this.properties = new ColumnProperties(settings);
    properties.forEach((column, property) -> {
      if (column.group == Column.Group.REGISTRATION) {
        this.properties.put(column, property);
      }
    });
  }

  public Registration(@NotNull Person person) {
    if (person != null)
      init(person);
  }

  public Registration(Person person, Registration registration) {
    init(person);

    merge(registration.getProperties());
//    registerSilentProperty.set(true); // Don't trigger registered change confirmation
//    getProperties().setIfExists(registration.getProperties());
//    registerSilentProperty.set(false);
//    setRegistered(getRegisteredDate() != null, true);
  }

  public boolean setDefaultRegistrationType() {
    String type = settings.getDefaultRegistrationType();
    if (type != null) {
      setRegistrationType(type);
      return true;
    }
    return false;
  }

  void init(Person person) {
    if (initialized) {
      throw new AppQuitException("Registration has already been initialized");
    }
    this.person = person;
    if (this.settings != null && this.settings != person.settings) {
      throw new AppQuitException("Person and Registration settings are different");
    }
    this.settings = person.settings;

    if (properties == null)
      properties = new ColumnProperties(settings);

    settings.getColumnsByGroup(Column.Group.REGISTRATION).forEach(column -> {
      if (properties.get(column) == null) {
        Property<?> property = settings.newProperty(column);
        if (property != null)
          properties.put(column, property);
      }
    });

    Property<?> property = properties.get(settings.getColumn(Column.Id.REGISTRATION_TYPE));
    if (property instanceof StringProperty) {
      registrationTypeProperty = (StringProperty) property;
    } else {
      registrationTypeProperty = null;
    }

    property = properties.get(settings.getColumn(Column.Id.REGISTER_DATE));
    if (property instanceof GsonDateProperty) {
      registeredDateProperty = (GsonDateProperty) property;
    } else {
      registeredDateProperty = null;
    }

    property = properties.get(settings.getColumn(Column.Id.REGISTERED));
    if (property instanceof BooleanProperty) {
      registeredProperty = (BooleanProperty) property;
      registeredProperty.set(getRegisteredDate() != null);
    } else if (property instanceof GsonBooleanProperty) {
      registeredProperty = ((GsonBooleanProperty) property).getBooleanPropertyBinding();
      registeredProperty.set(getRegisteredDate() != null);
    } else {
      registeredProperty = null;
    }

    if (registeredProperty != null && registeredDateProperty != null) {
//      registerSilentProperty = createRegisterDateBinding(registeredProperty, registeredDateProperty);
      ChangeListener<Boolean> registeredPropertyListener = new ChangeListener<Boolean>() {
        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean register) {
          runLaterSafe(() -> {
            observable.removeListener(this);
            registeredProperty.set(!register);
            if (register) {
              Person.RegistrationCheckProcess registrationCheckProcess = getPerson().newRegistrationCheckProcess();
              if ((registrationCheckProcess.sameTypeDeny(getRegistrationType()) ||
                  registrationCheckProcess.checkGracePeriod(getRegistrationType())) &&
                  registrationCheckProcess.checkSameTypeInRow(getRegistrationType())) {
                registeredProperty.set(true);
                registeredDateProperty.set(new Date(ZonedDateTime.now()));
              }
            } else {
              if (confirmClearRegistration()) {
                registeredProperty.set(false);
                registeredDateProperty.set(null);
              }
            }
            observable.addListener(this);
          });
        }
      };
      registeredProperty.addListener(registeredPropertyListener);
      registerSilentProperty = new SimpleBooleanProperty();
      registerSilentPropertyListener = (observable, oldValue, newValue) -> {
        if (newValue) {
          registeredProperty.removeListener(registeredPropertyListener);
        } else {
          registeredProperty.addListener(registeredPropertyListener);
        }
      };
      registerSilentProperty.addListener(registerSilentPropertyListener);
    } else {
      registerSilentProperty = null;
    }

    allProperties = new ColumnProperties(settings);
    for (Column column : settings.columns) {
      property = properties.get(column);
      if (property == null)
        property = person.getProperties().get(column);
      if (property != null) {
        allProperties.put(column, property);
      }
    }

    setRegistered(getRegisteredDate() != null, true);

    initialized = true;
  }

  private boolean confirmClearRegistration() {
    return settings.dialogHandler.confirm("Oled kindel, et tahad " + getRegistrationType().toLowerCase() + " registreerimist tühistada?",
        person.getDisplayInfo());
  }

  public boolean isRemoved() {
    return removed;
  }

  public void remove() {
    if (!initialized || removed)
      return;
    removed = true;

    setRegistered(false, true);
    setRegisteredDate(null);
    if (person != null)
      person.getRegistrations().remove(this);
    person = null;

    if (registerSilentProperty != null && registerSilentPropertyListener != null) {
      registerSilentProperty.set(true); // removes listener from registeredProperty
      registerSilentProperty.removeListener(registerSilentPropertyListener);
    }
    registerSilentPropertyListener = null;

    properties = null;

    allProperties.clear();
    allProperties = null;

    registrationTypeProperty = null;

    registeredProperty.unbind();
    registeredProperty = null;
    registerSilentProperty = null;
    registeredDateProperty = null;

  }

  public void reset() {
    setRegistered(false, true);
    setRegisteredDate(null);
    setDefaultRegistrationType();
  }

  public boolean isReset(){
    return !isRegistered() && getRegistrationType().equals(settings.getDefaultRegistrationType());
  }

  public boolean isOnlyRegistration(){
    return getPerson().getRegistrations().size() == 1;
  }

  public boolean isGracePeriod() {
    Long remainingGracePeriod = remainingGracePeriodMillis();
    return remainingGracePeriodMillis() != null && remainingGracePeriod > 0;
  }

  public Long remainingGracePeriodMillis() {
    Date registrationDate = getRegisteredDate();
    if (registrationDate == null) return null;
    Duration graceDuration = Duration.of(settings.general.registerGracePeriod, ChronoUnit.MILLIS);
    LocalDateTime graceRegistrationDate = registrationDate.getLocalDateTime().plus(graceDuration);
    return ChronoUnit.MILLIS.between(LocalDateTime.now(), graceRegistrationDate);
  }

  public Person getPerson() {
    return person;
  }

  public ColumnProperties getProperties() {
    return properties;
  }

  public ColumnProperties getWithPersonProperties() {
    return allProperties;
  }

  public StringProperty registrationTypeProperty() {
    return registrationTypeProperty;
  }

  public void setRegistrationType(String type) {
    if (registrationTypeProperty != null)
      registrationTypeProperty.set(type);
  }

  public String getRegistrationType() {
    if (registrationTypeProperty == null || registrationTypeProperty.get() == null)
      return "";
    return registrationTypeProperty.get();
  }

  public BooleanProperty registeredProperty() {
    return registeredProperty;
  }

  public boolean isRegistered() {
    if (registeredProperty == null)
      return false;
    return registeredProperty.get();
  }

  public void setRegisteredNoConfirm(boolean registered) {
    setRegistered(registered, true);
    setRegisteredDate(registered ? new Date(ZonedDateTime.now()) : null);
  }

  public void setRegisteredNoConfirm(Date date) {
    setRegistered(date != null, true);
    setRegisteredDate(date);
  }

  public void setRegistered(boolean registered) {
    setRegistered(registered, false);
  }

  public void setRegistered(boolean registered, boolean silent) {
    if (registeredProperty == null)
      return;

    if (silent)
      registerSilentProperty.set(true);
    registeredProperty.set(registered);
    if (silent)
      registerSilentProperty.set(false);
  }

  public GsonDateProperty registeredDateProperty() {
    return registeredDateProperty;
  }

  public Date getRegisteredDate() {
    if (registeredDateProperty == null)
      return null;
    return registeredDateProperty.get();
  }

  public void setRegisteredDate(Date date) {
    if (registeredDateProperty != null)
      registeredDateProperty.set(date);
  }

  public void merge(ColumnProperties properties) {
    registerSilentProperty.set(true); // Don't trigger registered change confirmation
    getProperties().merge(properties);
    registerSilentProperty.set(false);
    setRegistered(getRegisteredDate() != null, true);
  }

  public boolean same(Registration r) {
    return ((getRegisteredDate() == null && r.getRegisteredDate() == null) ||
        (getRegisteredDate() != null && getRegisteredDate().equals(r.getRegisteredDate()))) &&
        getRegistrationType().equals(r.getRegistrationType());
  }

  public boolean equals(Registration r) {
    return properties.equals(r.properties);
  }

  private void runLaterSafe(Runnable run) {
    Platform.runLater(() -> {
      if (removed) return;
      run.run();
    });
  }

  @Override
  public String toString() {
    return properties.toString();
  }
}
