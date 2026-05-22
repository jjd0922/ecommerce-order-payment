package com.orderpayment.application.idempotency;

public class IdempotencyInFlightException extends RuntimeException {

    public IdempotencyInFlightException(String message) {
        super(message);
    }
}
