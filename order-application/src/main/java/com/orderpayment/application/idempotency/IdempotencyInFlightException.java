package com.orderpayment.application.idempotency;

import com.orderpayment.application.common.ApplicationException;

public class IdempotencyInFlightException extends ApplicationException {

    public IdempotencyInFlightException(String message) {
        super(message);
    }
}
