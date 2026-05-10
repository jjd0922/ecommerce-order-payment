package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("expire 는 만료된 예약을 만료 처리하고 이벤트를 발행한다")
    void expire_whenExpiredReservationsExist_thenExpireReservationsAndPublishEvents() {
        recoveryPort.expiredReservations = List.of(expiredReservation(now.minusMinutes(1)));

        ExpireInventoryReservationsResult result = service.expire();

        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.processedAt()).isEqualTo(now);
        assertThat(recoveryPort.requestedAt).isEqualTo(now);
        assertThat(eventPublisher.events).hasSize(1);
        assertThat(eventPublisher.events.get(0).eventType()).isEqualTo("InventoryReservationExpired");
    }

    @Test
    @DisplayName("expire 는 만료된 예약이 없으면 0건 결과를 반환한다")
    void expire_whenExpiredReservationsDoNotExist_thenReturnZeroCount() {
        recoveryPort.expiredReservations = List.of();

        ExpireInventoryReservationsResult result = service.expire();

        assertThat(result.expiredCount()).isZero();
        assertThat(result.processedAt()).isEqualTo(now);
        assertThat(eventPublisher.events).isEmpty();
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
