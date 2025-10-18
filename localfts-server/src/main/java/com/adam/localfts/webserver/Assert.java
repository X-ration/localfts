package com.adam.localfts.webserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class Assert {

    private static Logger LOGGER = LoggerFactory.getLogger(Assert.class);

    public static <EX extends RuntimeException> void isTrue(boolean expression, String message, Class<EX> clazz) {
        if(!expression) {
            try {
                Constructor<EX> constructor = clazz.getConstructor(String.class);
                throw constructor.newInstance(message);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                LOGGER.error("Creating exception instance error", e);
                throw new RuntimeException(message);
            }
        }
    }

}
