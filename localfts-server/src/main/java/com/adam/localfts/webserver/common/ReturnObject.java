package com.adam.localfts.webserver.common;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReturnObject <T>{

    private boolean success;
    private String message;
    private T data;

    public ReturnObject() {
    }

    public ReturnObject(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ReturnObject<T> fail(String message, T data) {
        return new ReturnObject<>(false, message, data);
    }

    public static <T> ReturnObject<T> fail(String message) {
        return fail(message, null);
    }

    public static <T> ReturnObject<T> success(String message, T data) {
        return new ReturnObject<>(true, message, data);
    }

    public static <T> ReturnObject<T> success(T data) {
        return success(null, data);
    }
}
