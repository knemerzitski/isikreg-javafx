package com.knemerzitski.isikreg.utils;

import com.knemerzitski.isikreg.person.PersonList;

import java.util.HashMap;
import java.util.Map;

public class PersonListTestUtils {


  public static Map<String,Integer> getRegisteredCountsByType(PersonList personList){
    return personList.getUnmodifiableList().stream().reduce(new HashMap<>(), (map, p) -> {
          if (p.getLatestRegisteredRegistration() != null) {
            map.merge(p.getLatestRegisteredRegistration().getRegistrationType(), 1, Integer::sum);
          }
          return map;
        },
        (m, m2) -> {
          m.putAll(m2);
          return m;
        });
  }

  private PersonListTestUtils(){}
}
