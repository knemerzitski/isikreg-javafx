package com.knemerzitski.isikreg.person;

import com.knemerzitski.isikreg.date.Date;
import com.knemerzitski.isikreg.settings.ColumnProperties;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.ui.RegistrationFormDialog;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.knemerzitski.isikreg.settings.columns.Column.Group.PERSON;

public class PersonListHelper {

  private final Settings settings;
  private final PersonList personList;

  public PersonListHelper(PersonList personList) {
    this.personList = personList;
    this.settings = personList.settings;
  }

  public Person insertPerson(@NotNull ColumnProperties properties) {
    Person person = new Person(settings, properties);
    Person existingPerson = personList.get(person.getPersonalCode());
    if (existingPerson != null) {
      Person.RegistrationCheckProcess registrationCheckProcess = existingPerson.newRegistrationCheckProcess();
      if (!registrationCheckProcess.sameTypeDeny(properties.getRegistrationType()) &&
          !registrationCheckProcess.checkGracePeriod(properties.getRegistrationType()))
        return null;
      if (!registrationCheckProcess.checkSameTypeInRow(properties.getRegistrationType()))
        return null;
    } else {
      if (!settings.general.insertPerson) {
        person.showNewPersonNotAllowedWarning();
        return null;
      }
    }

    Person addPerson = personList.add(person);
    if (addPerson != null) {
      addPerson.getLatestRegistration().setRegisteredNoConfirm(true);
      return addPerson;
    } else {
      return null;
    }
  }

  public Registration insertRegistrationShowForm(@NotNull Person person, @NotNull ColumnProperties formValues) {
    Person.RegistrationCheckProcess registrationCheckProcess = person.newRegistrationCheckProcess();
    if (!registrationCheckProcess.checkGracePeriod())
      return null;

    String currentType = null;
    Registration latestReg = person.getLatestRegisteredRegistration();
    if (latestReg != null) {
      currentType = latestReg.getRegistrationType();
    }

    RegistrationFormDialog regForm = new RegistrationFormDialog(settings, "Uut tüüpi registreerimine" +
        (currentType != null ? " (Hetkel " + currentType.toLowerCase() + " registreeritud)" : ""), "Registreeri", true);
    regForm.setValues(formValues);

    String type = person.getNextRegistrationType();
    if (type != null)
      regForm.setValue(Column.Id.REGISTRATION_TYPE, new SimpleStringProperty(type));

    regForm.getFormNodes().forEach((column, node) -> {
      if (column.group != Column.Group.REGISTRATION) {
        node.setDisable(true);

        // Enable if property is null or empty string
        Property<?> property = formValues.get(column);
        if (property != null) {
          Object value = property.getValue();
          if (value == null || value instanceof String && ((String) value).trim().isEmpty()) {
            node.setDisable(false);
          }
        }
      }
    });

    Optional<ColumnProperties> newPropsOpt = regForm.showAndWait();
    if (!newPropsOpt.isPresent())
      return null;

    ColumnProperties newProps = newPropsOpt.get();

    String desiredType = newProps.getRegistrationType();

    // check type of registration
    if (!registrationCheckProcess.checkSameTypeInRow(desiredType))
      return null;

    Registration r = person.getOrNewRegistration(desiredType);
    r.getWithPersonProperties().setIfExists(newProps);
    r.getProperties().updateFormAutoFillIndex();
    person.getProperties().updateFormAutoFillIndex();
    r.setRegisteredNoConfirm(true);
    return r;
  }

  public Registration insertRegistrationConfirm(@NotNull Person person, @NotNull String registrationType) {
    Person.RegistrationCheckProcess registrationCheckProcess = person.newRegistrationCheckProcess();
    if (!registrationCheckProcess.sameTypeDeny(registrationType) &&
        !registrationCheckProcess.checkGracePeriod(registrationType))
      return null;
    if (!registrationCheckProcess.checkSameTypeInRow(registrationType))
      return null;


    // check grace period and type of reg
    Registration r = person.getOrNewRegistration(registrationType);
    r.setRegisteredNoConfirm(true);
    return r;
  }

  public void updatePersonalCodeShowForm(Registration registration) {
    if (!settings.general.updatePerson)
      return;

    RegistrationFormDialog regForm = new RegistrationFormDialog(settings, "Muuda isikukoodi", "Muuda isikukoodi", true);
    regForm.setValues(registration.getWithPersonProperties());

    regForm.getFormNodes().forEach((column, node) -> {
      if (column.id != Column.Id.PERSONAL_CODE) {
        node.setDisable(true);
      }
    });

    Optional<ColumnProperties> newPropsOpt = regForm.showAndWait();
    if (newPropsOpt.isPresent()) {
      ColumnProperties newProps = newPropsOpt.get();
      Person p = registration.getPerson();
      if(!newProps.getPersonalCode().equals(p.getPersonalCode())){ // Personal code changed?
        if (personList.get(newProps.getPersonalCode()) == null) {
          p.getProperties().setIfExists(newProps);
          p.getProperties().updateFormAutoFillIndex();
        } else {
          settings.dialogHandler.warning(String.format("Isikukoodi muutmine ebaõnnestus!%nIsikukoodiga '%s' isik on juba olemas!", newProps.getPersonalCode()),
              p.getDisplayInfo());
        }
      }
    }
  }

  public void updatePersonOrRegistrationShowForm(Registration registration) {
    List<String> labelList = new ArrayList<>();
    if (settings.general.updatePerson) {
      labelList.add("isikut");
    }
    labelList.add("registreeringut");

    RegistrationFormDialog regForm = new RegistrationFormDialog(settings,
        "Muuda " + String.join("/", labelList) +
            (registration.getRegisteredDate() != null ? " " + registration.getRegisteredDate() : "")
        , "Muuda", true);
    regForm.setValues(registration.getWithPersonProperties());
    regForm.setDisable(Column.Id.PERSONAL_CODE, true);

    if (!settings.general.updatePerson) {
      regForm.getFormNodes().forEach((column, node) -> {
        if (column.group == PERSON) {
          node.setDisable(true);
        }
      });
    }
    regForm.showAndWait().ifPresent(newProps -> {
      registration.getWithPersonProperties().setIfExists(newProps);
      registration.getProperties().updateFormAutoFillIndex();
      registration.getPerson().getProperties().updateFormAutoFillIndex();
    });
  }

  public boolean deletePeopleConfirm(List<Person> people, @NotNull Runnable beforeDelete) {
    if (!settings.general.deletePerson)
      return false;

    if (people.isEmpty())
      return true;

    String labelText = people.size() > 1 ?
        "Oled kindel, et tahad valitud isikud kustutada?" :
        "Oled kindel, et tahad valitud isiku kustutada?";

    GridPane gridPane = new GridPane();
    gridPane.setVgap(2);
    gridPane.setHgap(5);
    gridPane.setPadding(new Insets(5, 5, 5, 5));

    int rowIndex = 0;
    for (Person p : people) {
      String pn = p.getPersonalCode();
      if (pn != null)
        gridPane.add(new Label(pn), 2, rowIndex);
      String ln = p.getLastName();
      if (ln != null)
        gridPane.add(new Label(ln), 3, rowIndex);
      String fn = p.getFirstName();
      if (ln != null)
        gridPane.add(new Label(fn), 4, rowIndex);
      rowIndex++;
    }

    if (settings.dialogHandler.confirm(labelText, gridPane)) {
      beforeDelete.run();
      this.personList.removeAll(people);
      return true;
    }
    return false;
  }

  public boolean deleteRegistrationsConfirm(List<Registration> registrationList, @NotNull Runnable beforeDelete) {
    if (registrationList.isEmpty())
      return false;

    String labelText = registrationList.size() > 1 ?
        "Oled kindel, et tahad valitud registreerimisi tühistada?" :
        "Oled kindel, et tahad valitud registreerimist tühistada?";

    GridPane gridPane = new GridPane();
    gridPane.setVgap(2);
    gridPane.setHgap(5);
    gridPane.setPadding(new Insets(5, 5, 5, 5));

    int rowIndex = 0;
    for (Registration r : registrationList) {
      Person p = r.getPerson();
      String type = r.getRegistrationType();
      if (type != null)
        gridPane.add(new Label(type), 0, rowIndex);
      Date date = r.getRegisteredDate();
      if (date != null)
        gridPane.add(new Label(date.toString()), 1, rowIndex);
      String pn = p.getPersonalCode();
      if (pn != null)
        gridPane.add(new Label(pn), 2, rowIndex);
      String ln = p.getLastName();
      if (ln != null)
        gridPane.add(new Label(ln), 3, rowIndex);
      String fn = p.getFirstName();
      if (ln != null)
        gridPane.add(new Label(fn), 4, rowIndex);
      rowIndex++;
    }

    if (settings.dialogHandler.confirm(labelText, gridPane)) {
      beforeDelete.run();
      registrationList.forEach(r -> {
        Person p = r.getPerson();
        if (p.getRegistrations().size() > 1) {
          // remove only if it's not the last one
          r.remove();
        } else if (p.getRegistrations().size() == 1) {
          // clear registration and set default type
          r.reset();
        } else if (p.getRegistrations().isEmpty())
          p.cleanUpRegistrations(); // Add new empty registration
      });
      return true;
    }
    return false;
  }

  public boolean deleteAllRegistrationsConfirm(@NotNull Runnable beforeDelete) {
    ObservableList<Person> list = personList.getUnmodifiableList();
    if (list.isEmpty() || list.stream().noneMatch(p -> p.getRegistrations().stream().anyMatch(Registration::isRegistered)))
      return true;

    if (settings.dialogHandler.confirm("Oled kindel, et tahad registreerimised tühistada?", "Tühistan registreerimised?")) {
      beforeDelete.run();
      list.forEach(p -> {
        p.getRegistrations().forEach(r -> {
          r.setRegisteredNoConfirm(false);
        });
        p.cleanUpRegistrations();
      });
      return true;
    }
    return false;
  }

  public boolean deletePeopleAndRegistrationsConfirm(@NotNull Runnable beforeDelete) {
    if (!settings.general.deletePerson)
      return false;
    if (personList.getUnmodifiableList().isEmpty())
      return true;

    if (settings.dialogHandler.confirm("Oled kindel, et tahad nimekirja ära kustutada?", "Kustutan nimekirja?")) {
      beforeDelete.run();
      personList.clear();
      return true;
    }
    return false;
  }


}
