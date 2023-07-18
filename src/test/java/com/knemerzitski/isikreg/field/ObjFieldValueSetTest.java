package com.knemerzitski.isikreg.field;

import com.google.common.collect.Lists;
import com.knemerzitski.isikreg.settings.Settings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjFieldValueSetTest {

  @Test
  public void testCartesianProduct(){
    Stream<ObjFieldValueSet> valueSets = ObjFieldValueSet.cartesianProduct(Settings.class, Stream.of(
        "general.registerDuringGracePeriod", // 3
        "general.registerSameTypeInRow")); // 3
    List<String> abList = Stream.of("A", "B").collect(Collectors.toList());

    List<List<Object>> other = Lists.cartesianProduct(abList, valueSets.collect(Collectors.toList()));
    assertEquals(4 * 4 * 2, other.size());
  }

}
