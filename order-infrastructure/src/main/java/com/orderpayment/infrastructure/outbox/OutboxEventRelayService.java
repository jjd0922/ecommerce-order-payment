package com.orderpayment.infrastructure.outbox;

import com.orderpayment.infrastructure.event.LoggingDomainEventPublisherAdapter;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OutboxEventRelayService {

    private static final int DEFAULT_BATCH_SIZE = 100;

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final LoggingDomainEventPublisherAdapter loggingDomainEventPublisherAdapter;

    @Transactional
    public int publishPendingEvents() {
        int publishedCount = 0;
        for (OutboxEventJpaEntity event : outboxEventJpaRepository.findPendingForUpdateSkipLocked(
                OutboxEventStatus.PENDING.name(),
                DEFAULT_BATCH_SIZE
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
