package com.enterprise.payment.ledger.domain;

public class InvalidJournalException extends RuntimeException {
    public InvalidJournalException(String message) { super(message); }
}
