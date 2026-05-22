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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpireInventoryReservationsService implements ExpireInventoryReservationsUseCase {

    private final InventoryReservationRecoveryPort inventoryReservationRecoveryPort;
    private final CurrentTimePort currentTimePort;
    private final DomainEventPublisherPort domainEventPublisherPort;
    private final int batchSize;

    public ExpireInventoryReservationsService(
            InventoryReservationRecoveryPort inventoryReservationRecoveryPort,
            CurrentTimePort currentTimePort,
            DomainEventPublisherPort domainEventPublisherPort,
            @Value("${order-payment.inventory-reservation.expiration.batch-size:100}") int batchSize
    ) {
        this.inventoryReservationRecoveryPort = inventoryReservationRecoveryPort;
        this.currentTimePort = currentTimePort;
        this.domainEventPublisherPort = domainEventPublisherPort;
        this.batchSize = batchSize;
    }

    @Override
    @Transactional
    public ExpireInventoryReservationsResult expire() {
        LocalDateTime now = currentTimePort.now();
        List<InventoryReservation> expiredReservations =
                inventoryReservationRecoveryPort.expireExpiredReservations(now, batchSize);
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
