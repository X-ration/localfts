package com.adam.localfts.webserver.exception;

public class InvalidRangeException extends RuntimeException{

    public InvalidRangeException() {
        super();
    }

    public InvalidRangeException(String message) {
        super(message);
    }

}
