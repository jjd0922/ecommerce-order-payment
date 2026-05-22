package com.orderpayment.application.payment;

import com.orderpayment.application.common.ApplicationException;

public class IdempotencyKeyConflictException extends ApplicationException {

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
