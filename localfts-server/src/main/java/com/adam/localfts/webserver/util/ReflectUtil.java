package com.adam.localfts.webserver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.lang.reflect.Field;

public class ReflectUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectUtil.class);

    public static <T> T getFieldValue(Object object, String fieldName, Class<T> clazz) {
        Assert.notNull(object, "object is null!");
        Assert.notNull(fieldName, "fieldName is null!");
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object fieldValueObject = field.get(object);
            if(fieldValueObject == null) {
                return null;
            } else {
                return (T) fieldValueObject;
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting field {}'s value of object {}, ex.type={}, ex.message={}", fieldName, object, e.getClass().getName(), e.getMessage());
            return null;
        }
    }

}
