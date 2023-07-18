package com.knemerzitski.isikreg.field;

import com.knemerzitski.isikreg.settings.Settings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObjField {

  private static final List<Boolean> BOOLEAN_VALUES = Stream.of(true, false).collect(Collectors.toList());
  private static final List<Settings.Rule> RULE_VALUES = Stream.of(Settings.Rule.values()).collect(Collectors.toList());

  public static Stream<ObjField> of(Class<?> clazz, Stream<String> fieldPaths) {
    return fieldPaths.map(fieldPath -> new ObjField(clazz, fieldPath));
  }

  private final List<Field> fields;
  private final List<?> possibleValues;

  public ObjField(Class<?> clazz, String fieldPathStr){
    String[] fieldPathArr = fieldPathStr.split("\\.");
    if (fieldPathArr.length == 0) throw new IllegalArgumentException("fieldPath cannot be empty");

    fields = new ArrayList<>();

    for (String fieldName : fieldPathArr) {
      boolean found = false;
      for (Field field : clazz.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) continue;
        if (field.getName().equals(fieldName)) {
          fields.add(field);
          clazz = field.getType();
          found = true;
          break;
        }
      }
      if (!found) {
        throw new RuntimeException(String.format("Field name '%s' doesn't exist in class '%s'!", fieldName, clazz));
      }
    }

    Class<?> lastType = getLastField().getType();

    if(lastType.isAssignableFrom(boolean.class) ||
        lastType.isAssignableFrom(Boolean.class)){
      possibleValues = BOOLEAN_VALUES;
    }else if(lastType.isAssignableFrom(Settings.Rule.class)){
      possibleValues = RULE_VALUES;
    }else{
      throw new UnsupportedOperationException(String.format("FieldValues doesn't support type %s", lastType));
    }
  }


  public Object get(Object obj) throws IllegalAccessException {
    for (int i = 0; i < fields.size() - 1; i++) {
      Field field = fields.get(i);
      obj = field.get(obj);
    }

    return getLastField().get(obj);
  }

  public void set(Object obj, Object value) throws IllegalAccessException {
    for (int i = 0; i < fields.size() - 1; i++) {
      Field field = fields.get(i);
      obj = field.get(obj);
    }

    getLastField().set(obj, value);
  }

  public void set(Object obj, int index) throws IllegalAccessException {
    set(obj, possibleValues.get(index));
  }
  public List<ObjFieldValue> createFieldValueList(){
    List<ObjFieldValue> list = new ArrayList<>();
    for(int i = 0; i < possibleValues.size(); i++){
      if(isRule() && i == 1){ // CONFIRM
        list.add(new ObjFieldValue(this, i, true));
        list.add(new ObjFieldValue(this, i, false));
      }else{
        list.add(new ObjFieldValue(this, i));
      }

    }
    return list;
  }


  private Field getLastField() {
    return fields.get(fields.size()-1);
  }

  public Object getPossibleValue(int index){
    return possibleValues.get(index);
  }

  public int valuesCount(){
    return possibleValues.size();
  }

  protected List<Field> getFields() {
    return fields;
  }

  public String getName(){
    return fields.stream().map(Field::getName).collect(Collectors.joining("."));
  }

  private boolean isBoolean(){
    return possibleValues == BOOLEAN_VALUES;
  }

  private boolean isRule(){
    return possibleValues == RULE_VALUES;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    ObjField that = (ObjField) o;

    return new EqualsBuilder().append(getFields(), that.getFields()).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(getFields()).toHashCode();
  }



  @Override
  public String toString() {
    return getName();
  }
}
