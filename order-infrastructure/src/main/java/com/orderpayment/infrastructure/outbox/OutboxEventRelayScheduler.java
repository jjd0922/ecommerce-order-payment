package com.orderpayment.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelayScheduler {

    private final OutboxEventRelayService outboxEventRelayService;

    @Scheduled(
            initialDelayString = "${order-payment.outbox.relay.initial-delay:5000}",
            fixedDelayString = "${order-payment.outbox.relay.fixed-delay:5000}"
    )
    public void publishPendingEvents() {
        int publishedCount = outboxEventRelayService.publishPendingEvents();
        if (publishedCount > 0) {
            log.info("published outbox events. publishedCount={}", publishedCount);
        }
    }
}
