package com.orderpayment.infrastructure.outbox;

import com.orderpayment.infrastructure.event.LoggingDomainEventPublisherAdapter;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OutboxEventRelayService {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final LoggingDomainEventPublisherAdapter loggingDomainEventPublisherAdapter;

    @Value("${order-payment.outbox.relay.batch-size:100}")
    private int batchSize;

    @Transactional
    public int publishPendingEvents() {
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
