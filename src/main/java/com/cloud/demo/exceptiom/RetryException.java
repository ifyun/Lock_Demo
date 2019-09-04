package com.cloud.demo.exceptiom;

public class RetryException extends RuntimeException {
    public RetryException(String message) {
        super(message);
    }
}
