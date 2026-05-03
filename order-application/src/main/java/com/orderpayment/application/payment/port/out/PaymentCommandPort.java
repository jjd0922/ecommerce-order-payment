package com.orderpayment.application.payment.port.out;

import com.orderpayment.domain.payment.Payment;

public interface PaymentCommandPort {

    void savePayment(Payment payment);
}
