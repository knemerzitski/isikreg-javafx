package com.knemerzitski.isikreg.field;

import com.knemerzitski.isikreg.settings.Settings;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ObjFieldValueTest {

  @Test
  public void testEquals() {
    ObjField field1 = new ObjField(Settings.class, "smartCard.registerPersonNotInList");
    ObjField field2 = new ObjField(Settings.class, "smartCard.registerPersonNotInList");
    ObjField field3 = new ObjField(Settings.class, "smartCard.quickNewPersonRegistration");

    ObjFieldValue value1_0 = new ObjFieldValue(field1, 0);
    ObjFieldValue value1_1 = new ObjFieldValue(field1, 1);
    assertNotEquals(value1_0, value1_1);

    ObjFieldValue value2_0 = new ObjFieldValue(field2, 0);
    ObjFieldValue value2_1 = new ObjFieldValue(field2, 1);
    assertNotEquals(value2_0, value2_1);

    ObjFieldValue value3_0 = new ObjFieldValue(field3, 0);
    ObjFieldValue value3_1 = new ObjFieldValue(field3, 1);

    assertEquals(value1_0, value2_0);
    assertEquals(value1_1, value2_1);

    assertNotEquals(value1_0, value3_0);
    assertNotEquals(value1_1, value3_1);
    assertNotEquals(value1_0, value3_1);
    assertNotEquals(value1_1, value3_0);

    Set<ObjFieldValue> set = Stream.of(value1_0, value1_1, value2_0, value2_1, value3_0).collect(Collectors.toSet());
    assertEquals(3, set.size());
  }

}
