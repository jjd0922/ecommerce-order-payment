package com.orderpayment.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
public class OutboxEventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OutboxEventJpaEntity() {
    }

    public OutboxEventJpaEntity(
            String id,
            String eventType,
            String aggregateId,
            String payload,
            LocalDateTime occurredAt,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.retryCount = 0;
    }

    public String id() {
        return id;
    }

    public String eventType() {
        return eventType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String payload() {
        return payload;
    }

    public OutboxEventStatus status() {
        return status;
    }

    public LocalDateTime occurredAt() {
        return occurredAt;
    }

    public void markPublished(LocalDateTime publishedAt) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void markFailed(String errorMessage) {
        this.status = OutboxEventStatus.FAILED;
        this.retryCount++;
        this.lastError = errorMessage;
    }
}
