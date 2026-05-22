package com.orderpayment.application.idempotency;

public class IdempotencyRecordAlreadyExistsException extends RuntimeException {

    public IdempotencyRecordAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
