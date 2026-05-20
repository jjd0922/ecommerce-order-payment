package com.orderpayment.infrastructure.payment;

import com.orderpayment.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(name = "approval_requested_at")
    private LocalDateTime approvalRequestedAt;

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    protected PaymentJpaEntity() {
    }

    public PaymentJpaEntity(
            String id,
            String orderId,
            BigDecimal amount,
            String idempotencyKey,
            PaymentStatus status,
            LocalDateTime approvalRequestedAt,
            String pgTransactionId
    ) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.approvalRequestedAt = approvalRequestedAt;
        this.pgTransactionId = pgTransactionId;
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

    public LocalDateTime approvalRequestedAt() {
        return approvalRequestedAt;
    }

    public String pgTransactionId() {
        return pgTransactionId;
    }
}
