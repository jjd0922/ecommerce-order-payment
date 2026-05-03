package com.orderpayment.application.payment.port.out;

import com.orderpayment.domain.payment.PaymentId;

public interface PaymentIdGeneratorPort {

    PaymentId generate();
}
