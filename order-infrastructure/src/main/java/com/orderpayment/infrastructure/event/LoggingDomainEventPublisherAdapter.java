package com.orderpayment.infrastructure.event;

import com.orderpayment.infrastructure.outbox.OutboxEventJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingDomainEventPublisherAdapter {

    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisherAdapter.class);

    public void publish(OutboxEventJpaEntity event) {
        log.info(
                "outboxEvent published id={} type={} aggregateId={} occurredAt={}",
                event.id(),
                event.eventType(),
                event.aggregateId(),
                event.occurredAt()
        );
    }
}
