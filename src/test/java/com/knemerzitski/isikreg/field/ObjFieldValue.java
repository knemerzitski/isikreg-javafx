package com.knemerzitski.isikreg.field;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ObjFieldValue {

  private final ObjField objField;
  private final int index;
  private final Object data;

  public ObjFieldValue(ObjField objField, int index, Object data) {
    this.objField = objField;
    this.index = index;
    this.data = data;
  }

  public ObjFieldValue(ObjField objField, int index){
    this.objField = objField;
    this.index = index;
    this.data = null;
  }

  public Object get(Object obj) throws IllegalAccessException {
    return objField.get(obj);
  }

  public void set(Object obj) throws IllegalAccessException {
    objField.set(obj, index);
  }

  public ObjField getTargetField() {
    return objField;
  }

  public Class<?> getRootClass(){
    return objField.getFields().get(0).getType();
  }


  public int getIndex() {
    return index;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    ObjFieldValue that = (ObjFieldValue) o;

    return new EqualsBuilder().append(getIndex(), that.getIndex()).append(objField, that.objField).append(data, that.data).isEquals();
  }

  public Object getData() {
    return data;
  }

  public Boolean getDataAsBoolean() {
    return (Boolean)data;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(objField).append(getIndex()).append(data).toHashCode();
  }

  public String getVariableName(){
    return objField.getName();
  }

  public String getValueName(){
    return objField.getPossibleValue(index).toString();
  }

  public String getName(){
    return objField.getName() + "=" + getValueName() + (data != null ? "(" + data + ")" : "");
  }

  @Override
  public String toString() {
    return getName();
  }
}
