package com.orderpayment.infrastructure.payment;

import com.orderpayment.application.payment.port.out.PaymentApprovalPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import com.orderpayment.domain.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MockPaymentApprovalAdapter implements PaymentApprovalPort {

    @Override
    public PaymentApprovalResult approve(Payment payment) {
        return PaymentApprovalResult.approved("mock-pg-" + payment.id().value());
    }
}
