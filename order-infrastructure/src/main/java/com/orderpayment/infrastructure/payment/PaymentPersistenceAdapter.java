package com.orderpayment.infrastructure.payment;

import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentQueryPort;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.Payment;
import com.orderpayment.domain.payment.PaymentId;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements PaymentQueryPort, PaymentCommandPort {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment getPayment(PaymentId paymentId) {
        return paymentJpaRepository.findById(paymentId.value().toString())
                .map(this::toDomain)
                .orElseThrow(() -> new DomainException("payment not found"));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return paymentJpaRepository.findByIdempotencyKey(idempotencyKey.value())
                .map(this::toDomain);
    }

    @Override
    public void savePayment(Payment payment) {
        paymentJpaRepository.save(toEntity(payment));
    }

    private Payment toDomain(PaymentJpaEntity entity) {
        return Payment.restore(
                new PaymentId(UUID.fromString(entity.id())),
                new OrderId(UUID.fromString(entity.orderId())),
                new Money(entity.amount()),
                new IdempotencyKey(entity.idempotencyKey()),
                entity.status()
        );
    }

    private PaymentJpaEntity toEntity(Payment payment) {
        return new PaymentJpaEntity(
                payment.id().value().toString(),
                payment.orderId().value().toString(),
                payment.amount().amount(),
                payment.idempotencyKey().value(),
                payment.status()
        );
    }
}
