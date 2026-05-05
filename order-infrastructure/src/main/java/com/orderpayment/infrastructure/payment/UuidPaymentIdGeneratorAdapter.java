package com.orderpayment.infrastructure.payment;

import com.orderpayment.application.payment.port.out.PaymentIdGeneratorPort;
import com.orderpayment.domain.payment.PaymentId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidPaymentIdGeneratorAdapter implements PaymentIdGeneratorPort {

    @Override
    public PaymentId generate() {
        return new PaymentId(UUID.randomUUID());
    }
}
