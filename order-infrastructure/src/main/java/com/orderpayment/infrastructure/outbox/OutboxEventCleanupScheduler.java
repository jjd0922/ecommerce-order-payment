package com.orderpayment.infrastructure.outbox;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventCleanupScheduler {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Value("${order-payment.outbox.cleanup.retention-days:7}")
    private long retentionDays;

    @Transactional
    @Scheduled(
            initialDelayString = "${order-payment.outbox.cleanup.initial-delay:600000}",
            fixedDelayString = "${order-payment.outbox.cleanup.fixed-delay:3600000}"
    )
    public void deleteOldPublishedEvents() {
        LocalDateTime publishedBefore = LocalDateTime.now().minus(Duration.ofDays(retentionDays));
        int deletedCount = outboxEventJpaRepository.deletePublishedBefore(
                OutboxEventStatus.PUBLISHED,
                publishedBefore
        );
        if (deletedCount > 0) {
            log.info("deleted old published outbox events. deletedCount={}, publishedBefore={}",
                    deletedCount,
                    publishedBefore
            );
        }
    }
}
