package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpireInventoryReservationsServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 5, 10, 0);

    @Mock
    private InventoryReservationRecoveryPort recoveryPort;

    @Mock
    private DomainEventPublisherPort eventPublisher;

    @Captor
    private ArgumentCaptor<List<DomainEvent>> eventsCaptor;

    private ExpireInventoryReservationsService service;

    @BeforeEach
    void setUp() {
        service = new ExpireInventoryReservationsService(
                recoveryPort,
                () -> now,
                eventPublisher,
                100
        );
    }

    @Test
    @DisplayName("expire 는 만료된 예약을 만료 처리하고 이벤트를 발행한다")
    void expire_whenExpiredReservationsExist_thenExpireReservationsAndPublishEvents() {
        when(recoveryPort.expireExpiredReservations(now, 100))
                .thenReturn(List.of(expiredReservation(now.minusMinutes(1))));

        ExpireInventoryReservationsResult result = service.expire();

        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.processedAt()).isEqualTo(now);
        verify(eventPublisher).publishAll(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue())
                .hasSize(1)
                .first()
                .extracting(DomainEvent::eventType)
                .isEqualTo("InventoryReservationExpired");
    }

    @Test
    @DisplayName("expire 는 만료된 예약이 없으면 0건 결과를 반환한다")
    void expire_whenExpiredReservationsDoNotExist_thenReturnZeroCount() {
        when(recoveryPort.expireExpiredReservations(now, 100)).thenReturn(List.of());

        ExpireInventoryReservationsResult result = service.expire();

        assertThat(result.expiredCount()).isZero();
        assertThat(result.processedAt()).isEqualTo(now);
        verify(eventPublisher).publishAll(eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).isEmpty();
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
}
