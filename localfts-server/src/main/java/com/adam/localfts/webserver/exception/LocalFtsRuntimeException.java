package com.adam.localfts.webserver.exception;

public class LocalFtsRuntimeException extends RuntimeException{

    public LocalFtsRuntimeException() {
        super();
    }

    public LocalFtsRuntimeException(String message) {
        super(message);
    }

    public LocalFtsRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
