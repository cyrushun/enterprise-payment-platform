package com.enterprise.payment.account.application;

public class InvalidAccountOperationException extends RuntimeException {
    public InvalidAccountOperationException(String message) { super(message); }
}
