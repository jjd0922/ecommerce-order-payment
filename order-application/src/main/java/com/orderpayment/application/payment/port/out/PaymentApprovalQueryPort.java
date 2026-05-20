package com.orderpayment.application.payment.port.out;

import com.orderpayment.domain.payment.Payment;
import java.util.Optional;

public interface PaymentApprovalQueryPort {

    Optional<PaymentApprovalResult> findApprovalResult(Payment payment);
}
