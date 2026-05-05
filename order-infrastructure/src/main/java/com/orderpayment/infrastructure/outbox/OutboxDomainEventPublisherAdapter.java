package com.orderpayment.infrastructure.outbox;

import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.domain.common.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxDomainEventPublisherAdapter implements DomainEventPublisherPort {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final OutboxEventPayloadSerializer outboxEventPayloadSerializer;

    @Override
    public void publishAll(List<DomainEvent> events) {
        LocalDateTime createdAt = LocalDateTime.now();
        List<OutboxEventJpaEntity> outboxEvents = events.stream()
                .map(event -> toEntity(event, createdAt))
                .toList();
        outboxEventJpaRepository.saveAll(outboxEvents);
    }

    private OutboxEventJpaEntity toEntity(DomainEvent event, LocalDateTime createdAt) {
        return new OutboxEventJpaEntity(
                UUID.randomUUID().toString(),
                event.eventType(),
                event.aggregateId(),
                outboxEventPayloadSerializer.serialize(event),
                event.occurredAt(),
                createdAt
        );
    }
}
