package com.orderpayment.infrastructure.inventory;

import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.InventoryReservationQueryPort;
import com.orderpayment.application.payment.port.out.InventoryReservationRecoveryPort;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.inventory.InventoryReservationId;
import com.orderpayment.domain.inventory.InventoryReservationStatus;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InventoryReservationPersistenceAdapter
        implements InventoryReservationCommandPort, InventoryReservationQueryPort, InventoryReservationRecoveryPort {

    private final InventoryJpaRepository inventoryJpaRepository;
    private final InventoryReservationJpaRepository inventoryReservationJpaRepository;

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
    @Transactional(readOnly = true)
    public List<InventoryReservation> findHeldReservationsByOrderId(OrderId orderId) {
        return inventoryReservationJpaRepository.findByOrderIdAndStatus(
                        orderId.value().toString(),
                        InventoryReservationStatus.HELD
                ).stream()
                .map(InventoryReservationPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void confirmAll(List<InventoryReservation> reservations) {
        for (InventoryReservation reservation : orderedByProductId(reservations)) {
            int updatedRows = inventoryJpaRepository.confirm(
                    reservation.productId().value().toString(),
                    reservation.quantity()
            );
            if (updatedRows != 1) {
                throw new DomainException("held inventory is insufficient");
            }

            InventoryReservationJpaEntity entity = inventoryReservationJpaRepository.findById(
                    reservation.id().value().toString()
            ).orElseThrow(() -> new DomainException("reservation not found"));
            entity.confirm();
        }
    }

    @Override
    @Transactional
    public void releaseAll(List<InventoryReservation> reservations) {
        for (InventoryReservation reservation : orderedByProductId(reservations)) {
            int updatedRows = inventoryJpaRepository.release(
                    reservation.productId().value().toString(),
                    reservation.quantity()
            );
            if (updatedRows != 1) {
                throw new DomainException("held inventory is insufficient");
            }

            InventoryReservationJpaEntity entity = inventoryReservationJpaRepository.findById(
                    reservation.id().value().toString()
            ).orElseThrow(() -> new DomainException("reservation not found"));
            entity.release();
        }
    }

    @Override
    @Transactional
    public List<InventoryReservation> expireExpiredReservations(LocalDateTime now) {
        List<InventoryReservation> expiredReservations = new java.util.ArrayList<>();
        for (InventoryReservationJpaEntity reservation : inventoryReservationJpaRepository.findByStatusAndExpiresAtLessThanEqual(
                InventoryReservationStatus.HELD,
                now
        )) {
            int updatedRows = inventoryJpaRepository.release(reservation.productId(), reservation.quantity());
            if (updatedRows == 1) {
                reservation.expire();
                expiredReservations.add(toDomain(reservation));
            }
        }
        return expiredReservations;
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

    private static InventoryReservation toDomain(InventoryReservationJpaEntity entity) {
        return InventoryReservation.restore(
                InventoryReservationId.of(entity.id()),
                OrderId.of(entity.orderId()),
                ProductId.of(entity.productId()),
                entity.quantity(),
                entity.expiresAt(),
                entity.status()
        );
    }

    private static List<InventoryReservation> orderedByProductId(List<InventoryReservation> reservations) {
        return reservations.stream()
                .sorted(Comparator.comparing(reservation -> reservation.productId().value()))
                .toList();
    }
}
