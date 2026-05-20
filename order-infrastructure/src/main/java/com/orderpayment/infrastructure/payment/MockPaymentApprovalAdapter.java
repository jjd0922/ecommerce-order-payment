package com.orderpayment.infrastructure.payment;

import com.orderpayment.application.payment.port.out.PaymentApprovalPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalQueryPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import com.orderpayment.domain.payment.Payment;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MockPaymentApprovalAdapter implements PaymentApprovalPort, PaymentApprovalQueryPort {

    @Override
    public PaymentApprovalResult approve(Payment payment) {
        return PaymentApprovalResult.approved("mock-pg-" + payment.id().value());
    }

    @Override
    public Optional<PaymentApprovalResult> findApprovalResult(Payment payment) {
        return Optional.of(PaymentApprovalResult.approved("mock-pg-" + payment.id().value()));
    }
}
