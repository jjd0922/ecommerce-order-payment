package com.orderpayment.application.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.payment.dto.ExpireInventoryReservationsResult;
import com.orderpayment.application.payment.port.out.InventoryReservationRecoveryPort;
import com.orderpayment.application.payment.service.ExpireInventoryReservationsService;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.inventory.InventoryReservationId;
import com.orderpayment.domain.inventory.InventoryReservationStatus;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class ExpireInventoryReservationsServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 5, 10, 0);
    private final FakeInventoryReservationRecoveryPort recoveryPort = new FakeInventoryReservationRecoveryPort();
    private final FakeDomainEventPublisher eventPublisher = new FakeDomainEventPublisher();
    private final ExpireInventoryReservationsService service = new ExpireInventoryReservationsService(
            recoveryPort,
            () -> now,
            eventPublisher
    );

    @Test
    void expiresReservationsAndPublishesEvents() {
        recoveryPort.expiredReservations = List.of(expiredReservation(now.minusMinutes(1)));

        ExpireInventoryReservationsResult result = service.expire();

        assertEquals(1, result.expiredCount());
        assertEquals(now, result.processedAt());
        assertEquals(now, recoveryPort.requestedAt);
        assertEquals(1, eventPublisher.events.size());
        assertEquals("InventoryReservationExpired", eventPublisher.events.get(0).eventType());
    }

    @Test
    void returnsZeroWhenNoExpiredReservationExists() {
        recoveryPort.expiredReservations = List.of();

        ExpireInventoryReservationsResult result = service.expire();

        assertEquals(0, result.expiredCount());
        assertEquals(now, result.processedAt());
        assertEquals(0, eventPublisher.events.size());
    }

    private static InventoryReservation expiredReservation(LocalDateTime expiresAt) {
        return InventoryReservation.restore(
                InventoryReservationId.newId(),
                OrderId.newId(),
                ProductId.newId(),
                2,
                expiresAt,
                InventoryReservationStatus.EXPIRED
        );
    }

    private static class FakeInventoryReservationRecoveryPort implements InventoryReservationRecoveryPort {

        private LocalDateTime requestedAt;
        private List<InventoryReservation> expiredReservations = List.of();

        @Override
        public List<InventoryReservation> expireExpiredReservations(LocalDateTime now) {
            requestedAt = now;
            return expiredReservations;
        }
    }

    private static class FakeDomainEventPublisher implements DomainEventPublisherPort {

        private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publishAll(List<DomainEvent> events) {
            this.events.addAll(events);
        }
    }
}
