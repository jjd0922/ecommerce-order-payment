package com.orderpayment.infrastructure.outbox;

import com.orderpayment.infrastructure.event.LoggingDomainEventPublisherAdapter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxEventRelayService {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final LoggingDomainEventPublisherAdapter loggingDomainEventPublisherAdapter;
    private final Timer relayLatencyTimer;

    @Value("${order-payment.outbox.relay.batch-size:100}")
    private int batchSize;

    public OutboxEventRelayService(
            OutboxEventJpaRepository outboxEventJpaRepository,
            LoggingDomainEventPublisherAdapter loggingDomainEventPublisherAdapter,
            MeterRegistry meterRegistry
    ) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.loggingDomainEventPublisherAdapter = loggingDomainEventPublisherAdapter;
        this.relayLatencyTimer = Timer.builder("outbox.relay.latency")
                .description("Outbox relay execution latency")
                .register(meterRegistry);
        Gauge.builder("outbox.pending.size", outboxEventJpaRepository,
                        repository -> repository.countByStatus(OutboxEventStatus.PENDING))
                .description("Number of pending outbox events")
                .register(meterRegistry);
    }

    @Transactional
    public int publishPendingEvents() {
        return relayLatencyTimer.record(this::publishPendingEventsInternal);
    }

    private int publishPendingEventsInternal() {
        int publishedCount = 0;
        for (OutboxEventJpaEntity event : outboxEventJpaRepository.findPendingForUpdateSkipLocked(
                OutboxEventStatus.PENDING.name(),
                batchSize
        )) {
            try {
                loggingDomainEventPublisherAdapter.publish(event);
                event.markPublished(LocalDateTime.now());
                publishedCount++;
            } catch (RuntimeException exception) {
                event.markFailed(exception.getMessage());
            }
        }
        return publishedCount;
    }
}
