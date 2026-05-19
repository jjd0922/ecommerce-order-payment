package com.orderpayment.infrastructure.idempotency;

import com.orderpayment.domain.idempotency.IdempotencyRecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecordJpaEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Lob
    @Column(name = "response_body", columnDefinition = "JSON")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyRecordStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected IdempotencyRecordJpaEntity() {
    }

    public IdempotencyRecordJpaEntity(
            String key,
            String requestHash,
            String responseBody,
            IdempotencyRecordStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        this.key = key;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String key() {
        return key;
    }

    public String requestHash() {
        return requestHash;
    }

    public String responseBody() {
        return responseBody;
    }

    public IdempotencyRecordStatus status() {
        return status;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }
}
