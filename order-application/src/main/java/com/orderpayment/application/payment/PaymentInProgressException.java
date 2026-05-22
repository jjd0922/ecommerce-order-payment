package com.orderpayment.application.payment;

import com.orderpayment.application.common.ApplicationException;

public class PaymentInProgressException extends ApplicationException {

    public PaymentInProgressException(String message) {
        super(message);
    }
}
