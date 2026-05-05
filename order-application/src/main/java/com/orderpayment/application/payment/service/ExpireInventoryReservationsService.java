package com.orderpayment.application.payment.service;

import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.payment.dto.ExpireInventoryReservationsResult;
import com.orderpayment.application.payment.port.in.ExpireInventoryReservationsUseCase;
import com.orderpayment.application.payment.port.out.InventoryReservationRecoveryPort;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.inventory.InventoryReservationExpiredEvent;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpireInventoryReservationsService implements ExpireInventoryReservationsUseCase {

    private final InventoryReservationRecoveryPort inventoryReservationRecoveryPort;
    private final CurrentTimePort currentTimePort;
    private final DomainEventPublisherPort domainEventPublisherPort;

    @Override
    @Transactional
    public ExpireInventoryReservationsResult expire() {
        LocalDateTime now = currentTimePort.now();
        List<InventoryReservation> expiredReservations = inventoryReservationRecoveryPort.expireExpiredReservations(now);
        domainEventPublisherPort.publishAll(toEvents(expiredReservations, now));
        return new ExpireInventoryReservationsResult(expiredReservations.size(), now);
    }

    private static List<DomainEvent> toEvents(List<InventoryReservation> reservations, LocalDateTime occurredAt) {
        return reservations.stream()
                .map(reservation -> new InventoryReservationExpiredEvent(
                        reservation.id(),
                        reservation.orderId(),
                        reservation.productId(),
                        reservation.quantity(),
                        reservation.expiresAt(),
                        occurredAt
                ))
                .map(DomainEvent.class::cast)
                .toList();
    }
}
