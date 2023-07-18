package com.knemerzitski.isikreg.person;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.knemerzitski.isikreg.exception.AppQuitException;
import com.knemerzitski.isikreg.file.SafeSynchronizedStringFile;
import com.knemerzitski.isikreg.gson.GsonDateProperty;
import com.knemerzitski.isikreg.settings.ColumnProperties;
import com.knemerzitski.isikreg.settings.Settings;
import com.knemerzitski.isikreg.settings.columns.Column;
import com.knemerzitski.isikreg.settings.columns.ComboBoxColumn;
import com.knemerzitski.isikreg.threading.TaskExecutor;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class PersonList extends SafeSynchronizedStringFile<Map<String, JsonObject>, Person> {

  private static final Type MAP_STRING_PERSON_TYPE = new TypeToken<Map<String, Person>>() {
  }.getType();
  private static final Type MAP_STRING_JSON_OBJECT_TYPE = new TypeToken<Map<String, JsonObject>>() {
  }.getType();

  private class PersonTypeAdapterFactory implements TypeAdapterFactory {
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
      if (type.getType() == ColumnProperties.class) {
        return (TypeAdapter<T>) new ColumnProperties.ColumnPropertiesAdapter(settings, gson);
      } else if (type.getType() == GsonDateProperty.class) {
        return (TypeAdapter<T>) new GsonDateProperty.GsonDatePropertyAdapter(gson);
      }
      return delegate;
    }
  }

  private static class PersonListeners {
    final ChangeListener<String> registeredListener;
    final ChangeListener<String> personalCodeListener;
    final ChangeListener<Object> propertyListener;
    final ListChangeListener<Registration> listListener;

    public PersonListeners(ChangeListener<String> registeredListener,
                           ChangeListener<String> personalCodeListener,
                           ChangeListener<Object> propertyListener,
                           ListChangeListener<Registration> listListener) {
      this.registeredListener = registeredListener;
      this.personalCodeListener = personalCodeListener;
      this.propertyListener = propertyListener;
      this.listListener = listListener;
    }
  }

  private static final String EXT = ".json";


  protected final Settings settings;

  private final Gson gson;

  private final ObservableMap<String, Person> personMap = FXCollections.observableHashMap();
  private final ObservableList<Person> unmodifiableList;

  private final Map<Person, PersonListeners> personListenersMap = new IdentityHashMap<>();

  private final Map<String, IntegerProperty> registeredCountProperties = new LinkedHashMap<>();
  private final IntegerProperty sizeProperty = new SimpleIntegerProperty(0);

  private final BooleanProperty clearingProperty = new SimpleBooleanProperty();

  private boolean personMapToListListenerDisabled = false;


  public PersonList(Settings settings, Path path, TaskExecutor taskExecutor) throws IOException {
    super(path.resolveSibling(path.getFileName() + EXT), taskExecutor);
    setSaveDelay(settings.general.saveDelay);
    setSaveCompressedZip(settings.general.saveCompressedZip);

    this.settings = settings;

    // List is synchronized to personMap
    ObservableList<Person> list = FXCollections.observableArrayList();
    unmodifiableList = FXCollections.unmodifiableObservableList(list);
    personMap.addListener((MapChangeListener<String, Person>) c -> {
      if (personMapToListListenerDisabled)
        return;
      if (c.wasRemoved()) {
        Person p = c.getValueRemoved();
        list.remove(p);
      }
      if (c.wasAdded()) {
        Person p = c.getValueAdded();
        list.add(p);
      }
    });

    // Init registered count properties
    settings.getRegistrationTypes().forEach(type -> {
      registeredCountProperties.put(type, new SimpleIntegerProperty());
    });

    GsonBuilder gsonBuilder = new GsonBuilder()
        .registerTypeAdapterFactory(new PersonTypeAdapterFactory())
        .registerTypeAdapter(Person.class, new Person.PersonInstanceCreator(settings));

    if (!settings.general.saveCompressedZip) {
      gsonBuilder.setPrettyPrinting();
    }
    gson = gsonBuilder.create();
  }

  public ObservableList<Person> getUnmodifiableList() {
    return unmodifiableList;
  }

  public Person get(String personalCode) {
    return personMap.get(personalCode);
  }

  public Collection<Person> values() {
    return personMap.values();
  }

  public synchronized Person add(Person person, boolean write, boolean read) {
    Person existingPerson = personMap.get(person.getPersonalCode());
    if (existingPerson == person)
      return existingPerson; //already added
    Person addPerson = null;
    if (existingPerson == null) {
      if (!person.getPersonalCode().isEmpty()) {
        addListeners(person, read);
        personMap.put(person.getPersonalCode(), person);
        sizeProperty.set(personMap.size());
        addPerson = person;
      }
    } else {
      existingPerson.merge(person);
//      existingPerson.merge(person, false);
      addPerson = existingPerson;
    }
    if (addPerson != null && write)
      writeItem(addPerson);
    if (addPerson != null) {
      addPerson.updateAutoFillIndex();
    }
    return addPerson;
  }

  public Person add(Person person) {
    return add(person, true, false);
  }

  private boolean updatePersonalCode(String oldPersonalCode, Person person) {
    if (person.getPersonalCode().isEmpty()) return false;

    Person newPerson = personMap.get(person.getPersonalCode());
    if (newPerson == person) return true; // Already correct personal code

    Person thisPerson = personMap.get(oldPersonalCode);

    // Make sure person with that personal code doesn't already exist
    if (thisPerson != null && newPerson == null) {
      personMapToListListenerDisabled = true; // Prevents updating list on quick personMap value swapping
      personMap.remove(oldPersonalCode);
      deleteItem(new Person.Identity(oldPersonalCode));
      personMap.put(person.getPersonalCode(), person);
      writeItem(person);
      personMapToListListenerDisabled = false;
      return true;
    }
    return false;
  }

  public synchronized void removeAll(List<Person> people) {
    people.forEach(person -> {
      removeListenersExceptRegisteredProperty(person); // Prevent writing again from property change
      person.removeRegistrations(); // Triggers listeners
      removeListeners(person); // Remove all listeners
      personMap.remove(person.getPersonalCode());
      deleteItem(new Person.Identity(person.getPersonalCode()));
      person.removed();
    });
    sizeProperty.set(personMap.size());
  }

  private synchronized void remove(String personalCode) {
    Person existingPerson = personMap.get(personalCode);
    if (existingPerson != null) {
      removeListenersExceptRegisteredProperty(existingPerson); // Prevent writing again from property change
      existingPerson.removeRegistrations(); // Triggers listeners
      removeListeners(existingPerson); // Remove all listeners
      personMap.remove(personalCode);
      sizeProperty.set(personMap.size());
      deleteItem(new Person.Identity(personalCode));
      existingPerson.removed();
    }
  }

  public void remove(Person person) {
    remove(person.getPersonalCode());
  }

  public synchronized void clear() {
    clearingProperty.set(true);

    for (Iterator<Map.Entry<String, Person>> it = personMap.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, Person> entry = it.next();
      Person person = entry.getValue();
      removeListenersExceptRegisteredProperty(person);
      person.removeRegistrations(); // Trigger all listeners
      removeListeners(person);
      it.remove();
      person.removed();
    }

    sizeProperty.set(personMap.size());

    try {
      delete();
    } catch (IOException e) {
      throw new AppQuitException(e);
    }

    settings.columns.forEach(column -> {
      if (column.type == Column.Type.COMBOBOX) {
        ComboBoxColumn comboBoxColumn = (ComboBoxColumn) column;
        if (comboBoxColumn.hasForm()) {
          comboBoxColumn.form.resetAutoFill();
        }
      }
    });
    clearingProperty.set(false);
    System.gc();
  }

  private void addListeners(Person person, boolean read) {
    ChangeListener<String> registeredCountListener = (observable, oldType, newType) -> {
      IntegerProperty newCount = registeredCountProperties.get(newType);
      if (newCount != null) {
        newCount.set(newCount.get() + 1);
      }
      IntegerProperty oldCount = registeredCountProperties.get(oldType);
      if (oldCount != null) {
        oldCount.set(oldCount.get() - 1);
      }
    };
    if (read) {
      IntegerProperty count = registeredCountProperties.get(person.getRegisteredType());
      if (count != null) {
        count.set(count.get() + 1);
      }
    }

    person.registeredTypeProperty().addListener(registeredCountListener);

    ChangeListener<String> personalCodeListener = (observable, oldValue, newValue) -> {
      if (newValue != null && !newValue.isEmpty()) {
        if (!updatePersonalCode(oldValue, person)) {
          // Prevent personal code change if personMap rejected it
          person.personalCodeProperty().set(oldValue);
        }
      } else {
        // Prevent empty personal code
        person.personalCodeProperty().set(oldValue);
      }
    };
    person.personalCodeProperty().addListener(personalCodeListener);

    ChangeListener<Object> propListener = (observable, oldValue, newValue) -> {
      writeItem(person);
    };
    person.getProperties().values().forEach(prop -> prop.addListener(propListener));
    person.getRegistrations().forEach(r -> r.getProperties().values().forEach(prop -> prop.addListener(propListener)));
    ListChangeListener<Registration> listListener = c -> {
      while (c.next()) {
        c.getAddedSubList().forEach(r -> r.getProperties().values().forEach(prop -> prop.addListener(propListener)));
        c.getRemoved().forEach(r -> r.getProperties().values().forEach(prop -> prop.removeListener(propListener)));
      }
      writeItem(person);
    };
    person.getRegistrations().addListener(listListener);

    personListenersMap.put(person, new PersonListeners(registeredCountListener, personalCodeListener, propListener, listListener));
  }

  private void removeListenersExceptRegisteredProperty(Person person) {
    PersonListeners personListeners = personListenersMap.get(person);
    if (personListeners == null)
      return;
    person.personalCodeProperty().removeListener(personListeners.personalCodeListener);

    ChangeListener<Object> propListener = personListeners.propertyListener;
    person.getProperties().values().forEach(prop -> prop.removeListener(propListener));
    person.getRegistrations().forEach(r -> r.getProperties().values().forEach(prop -> prop.removeListener(propListener)));
    person.getRegistrations().removeListener(personListeners.listListener);
  }

  private void removeListeners(Person person) {
    // remove all listeners
    removeListenersExceptRegisteredProperty(person);
    PersonListeners personListeners = personListenersMap.get(person);
    if (personListeners == null)
      return;
    person.registeredTypeProperty().removeListener(personListeners.registeredListener);
    personListenersMap.remove(person);
  }

  @Override
  protected boolean read(InputStreamReader reader, String name) throws IOException {
    try {
      Map<String, Person> newList = gson.fromJson(reader, MAP_STRING_PERSON_TYPE);
      if (newList == null)
        return false;

      Collection<Person> values = newList.values();
      values.forEach(p -> {
        p.init();
        add(p, false, true);
      });

      return true;
    } catch (JsonSyntaxException e) {
      throw new JsonSyntaxException(e.getMessage() + "\n\"" + name + "\" vale JSON struktuur", e);
    }
  }

  /**
   * Pointless registrations will be cleaned up during verifying process.
   * So some registrations will not remain same after calling this method.
   * ColumnProperties with empty values become null in registrations and in person properties.
   *
   * @return Written file matches current PersonList state. Last was writing successful.
   * @throws IOException
   */
  public boolean verifyWritten() throws IOException, InterruptedException {
    waitForWritingFinished();

    if (personMap.isEmpty()) {
      return !super.exists();
    }

    return super.readInReader(reader -> {
      Map<String, Person> newMap = gson.fromJson(reader, MAP_STRING_PERSON_TYPE);
      if (newMap == null)
        return false;

      newMap.values().forEach(p -> p.init(false, true));
      personMap.values().forEach(p -> {
        p.cleanUpRegistrations();
        p.setEmptyStringPropertiesToNull();
      });

      return Stream.concat(personMap.keySet().stream(), newMap.keySet().stream()).allMatch(personalCode -> {
        Person p1 = newMap.get(personalCode);
        if (p1 == null) return false;
        Person p2 = personMap.get(personalCode);
        if (p2 == null) return false;
        return p1.equals(p2);
      });
    });
  }

  @Override
  protected boolean write(OutputStreamWriter writer, String name) throws IOException {
    gson.toJson(personMap, writer);
    writer.flush();
    return true;
  }

  @Override
  protected Map<String, JsonObject> startWriting(InputStreamReader reader, String name) throws IOException {
    try {
      return gson.fromJson(reader, MAP_STRING_JSON_OBJECT_TYPE);
    } catch (JsonSyntaxException e) {
      throw new JsonSyntaxException(e.getMessage() + "\n\"" + name + "\" vale JSON struktuur", e);
    }
  }

  @Override
  protected void write(Map<String, JsonObject> jsonPersonMap, Person person) throws IOException {
    String personalCode = person.getPersonalCode();
    if (personalCode == null || personalCode.isEmpty())
      throw new AppQuitException("Tried to write Person with empty PERSONAL_CODE");
    jsonPersonMap.put(personalCode, gson.toJsonTree(person).getAsJsonObject());
  }

  @Override
  protected void delete(Map<String, JsonObject> jsonPersonMap, Person person) {
    String personalCode = person.getPersonalCode();
    if (personalCode == null || personalCode.isEmpty())
      throw new AppQuitException("Tried to delete Person with empty PERSONAL_CODE");
    jsonPersonMap.remove(personalCode);
  }

  @Override
  protected boolean endWriting(OutputStreamWriter writer, String name, Map<String, JsonObject> container) throws IOException {
    gson.toJson(container, writer);
    writer.flush();
    return true;
  }

  ObservableMap<String, Person> getPersonMap() {
    return personMap;
  }

  public Map<String, IntegerProperty> registeredCountProperties() {
    return registeredCountProperties;
  }

  public IntegerProperty sizeProperty() {
    return sizeProperty;
  }

  public BooleanProperty clearingProperty() {
    return clearingProperty;
  }

  public boolean isEmpty() {
    return personMap.isEmpty();
  }

  public int size() {
    return personMap.size();
  }

  public boolean equals(PersonList other) {
    if (other == null) return false;
    if (personMap.size() != other.size()) return false;

    Collection<Person> remaining = other.values();
    for (Person p : values()) {
      boolean found = false;
      Iterator<Person> itr = remaining.iterator();
      while (itr.hasNext()) {
        Person p2 = itr.next();
        if (p.equals(p2)) {
          itr.remove();
          found = true;
          break;
        }
      }
      if (!found) return false;
    }
    return true;
  }

}
