package com.adam.localfts.webserver.common;

@FunctionalInterface
public interface FunctionThrowsException<T,R> {
    R apply(T t) throws Exception;
}
