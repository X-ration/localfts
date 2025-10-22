package com.adam.junit;

import java.util.HashMap;
import java.util.Map;

public class ClassCastTest {

//    @Test
    public void testMapCastWithType() {
        Map<String, Integer> map = new HashMap<>();
        map.put("1", 2);
        Object mapObject = (Object) map;
        Map<String, String> newMap = (Map<String, String>) mapObject;
        String value = newMap.get("1");
    }

}
