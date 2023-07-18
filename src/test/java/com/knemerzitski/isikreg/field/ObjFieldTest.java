package com.knemerzitski.isikreg.field;

import com.knemerzitski.isikreg.settings.Settings;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ObjFieldTest {

  @Test
  public void testGetSet() throws IllegalAccessException {
    ObjField fieldPath = new ObjField(Settings.class, "smartCard.registerPersonNotInList");

    Settings settings = new Settings();

    settings.smartCard.registerPersonNotInList = Settings.Rule.ALLOW;
    assertEquals(Settings.Rule.ALLOW, fieldPath.get(settings));

    settings.smartCard.registerPersonNotInList = Settings.Rule.DENY;
    assertEquals(Settings.Rule.DENY, fieldPath.get(settings));

    fieldPath.set(settings, Settings.Rule.CONFIRM);
    assertEquals(Settings.Rule.CONFIRM, fieldPath.get(settings));
    assertEquals(Settings.Rule.CONFIRM, settings.smartCard.registerPersonNotInList);

    fieldPath.set(settings, Settings.Rule.ALLOW);
    assertEquals(Settings.Rule.ALLOW, fieldPath.get(settings));
    assertEquals(Settings.Rule.ALLOW, settings.smartCard.registerPersonNotInList);
  }


  @Test
  public void testFieldValues() throws IllegalAccessException {
    ObjField fieldValues =  new ObjField(Settings.class, "smartCard.registerPersonNotInList");

    Settings settings = new Settings();

    Set<Settings.Rule> ruleValues = Stream.of(Settings.Rule.values()).collect(Collectors.toSet());
    for(int i = 0;i<fieldValues.valuesCount();i++){
      fieldValues.set(settings, i);
      assertEquals(settings.smartCard.registerPersonNotInList, fieldValues.get(settings));
      ruleValues.remove(settings.smartCard.registerPersonNotInList);
    }
    assertEquals(0, ruleValues.size());
  }


  @Test
  public void testEquals() throws IllegalAccessException {
    ObjField field1 = new ObjField(Settings.class, "smartCard.registerPersonNotInList");
    ObjField field2 = new ObjField(Settings.class, "smartCard.registerPersonNotInList");
    ObjField field3 = new ObjField(Settings.class, "smartCard.quickNewPersonRegistration");

    assertEquals(field1, field2);
    assertNotEquals(field1, field3);
  }

  @Test
  public void testToString() throws IllegalAccessException {
    ObjField field = new ObjField(Settings.class, "smartCard.registerPersonNotInList");

    assertEquals("smartCard.registerPersonNotInList", field.toString());
  }






}
