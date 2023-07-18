package com.knemerzitski.isikreg.field;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObjFieldValueSet {

  public static Stream<ObjFieldValueSet> cartesianProduct(Class<?> clazz, Stream<String> fieldNames){
    List<ObjField> fields = ObjField.of(clazz, fieldNames).collect(Collectors.toList());

    List<List<ObjFieldValue>> fieldValuesList = fields
        .stream()
        .map(ObjField::createFieldValueList)
        .collect(Collectors.toList());

    return Lists.cartesianProduct(fieldValuesList).stream().map(ObjFieldValueSet::new);
  }

  private final Set<ObjFieldValue> values;

  public ObjFieldValueSet() {
    this.values = new HashSet<>();
  }

  public ObjFieldValueSet(List<ObjFieldValue> values) {
    this.values = new HashSet<>(values);
  }

  public ObjFieldValueSet(Set<ObjFieldValue> values) {
    this.values = values;
  }


  public void set(Object obj) throws IllegalAccessException {
    for (ObjFieldValue fieldValue : values) {
      fieldValue.set(obj);
    }
  }

  public Set<ObjFieldValue> get() {
    return values;
  }

  public ObjFieldValue getByName(String variableName) {
    return values.stream()
        .filter(v -> v.getVariableName().equals(variableName)).findFirst()
        .orElseThrow(() -> new RuntimeException(String.format("Variable '%s' doesn't exist in '%s'",
            variableName,
            values.iterator().next().getRootClass())));
  }



  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    ObjFieldValueSet that = (ObjFieldValueSet) o;

    return new EqualsBuilder().append(values, that.values).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(values).toHashCode();
  }

  @Override
  public String toString() {
    return values.toString();
  }

}
