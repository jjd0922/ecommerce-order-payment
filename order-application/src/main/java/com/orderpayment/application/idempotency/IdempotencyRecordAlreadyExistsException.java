package com.orderpayment.application.idempotency;

import com.orderpayment.application.common.ApplicationException;

public class IdempotencyRecordAlreadyExistsException extends ApplicationException {

    public IdempotencyRecordAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
