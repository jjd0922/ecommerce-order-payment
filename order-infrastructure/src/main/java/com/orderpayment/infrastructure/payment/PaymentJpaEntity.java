package com.orderpayment.infrastructure.payment;

import com.orderpayment.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "payment")
public class PaymentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    protected PaymentJpaEntity() {
    }

    public PaymentJpaEntity(String id, String orderId, BigDecimal amount, String idempotencyKey, PaymentStatus status) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
    }

    public String id() {
        return id;
    }

    public String orderId() {
        return orderId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public PaymentStatus status() {
        return status;
    }
}
