package com.orderpayment.infrastructure.inventory;

import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.InventoryReservationRecoveryPort;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.inventory.InventoryReservationId;
import com.orderpayment.domain.inventory.InventoryReservationStatus;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryReservationPersistenceAdapter
        implements InventoryReservationCommandPort, InventoryReservationRecoveryPort {

    private final InventoryJpaRepository inventoryJpaRepository;
    private final InventoryReservationJpaRepository inventoryReservationJpaRepository;

    public InventoryReservationPersistenceAdapter(
            InventoryJpaRepository inventoryJpaRepository,
            InventoryReservationJpaRepository inventoryReservationJpaRepository
    ) {
        this.inventoryJpaRepository = inventoryJpaRepository;
        this.inventoryReservationJpaRepository = inventoryReservationJpaRepository;
    }

    @Override
    @Transactional
    public InventoryReservation hold(OrderId orderId, ProductId productId, int quantity, LocalDateTime expiresAt) {
        int updatedRows = inventoryJpaRepository.hold(productId.value().toString(), quantity);
        if (updatedRows != 1) {
            throw new DomainException("inventory is insufficient");
        }

        InventoryReservation reservation = InventoryReservation.hold(
                InventoryReservationId.newId(),
                orderId,
                productId,
                quantity,
                expiresAt
        );
        inventoryReservationJpaRepository.save(toEntity(reservation));
        return reservation;
    }

    @Override
    @Transactional
    public int releaseExpiredReservations(LocalDateTime now) {
        int releasedCount = 0;
        for (InventoryReservationJpaEntity reservation : inventoryReservationJpaRepository.findByStatusAndExpiresAtBefore(
                InventoryReservationStatus.HELD,
                now
        )) {
            int updatedRows = inventoryJpaRepository.release(reservation.productId(), reservation.quantity());
            if (updatedRows == 1) {
                reservation.expire();
                releasedCount++;
            }
        }
        return releasedCount;
    }

    private static InventoryReservationJpaEntity toEntity(InventoryReservation reservation) {
        return new InventoryReservationJpaEntity(
                reservation.id().value().toString(),
                reservation.orderId().value().toString(),
                reservation.productId().value().toString(),
                reservation.quantity(),
                reservation.expiresAt(),
                reservation.status()
        );
    }
}
