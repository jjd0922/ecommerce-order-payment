package com.orderpayment.application.payment.port.out;

import com.orderpayment.domain.payment.Payment;

public interface PaymentApprovalPort {

    PaymentApprovalResult approve(Payment payment);
}
