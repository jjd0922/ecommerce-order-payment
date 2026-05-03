package com.orderpayment.domain.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InventoryReservationTest {

    private final LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 3, 21, 0);

    @Test
    void createsHeldReservation() {
        InventoryReservation reservation = reservation();

        assertEquals(InventoryReservationStatus.HELD, reservation.status());
    }

    @Test
    void releasesReservation() {
        InventoryReservation reservation = reservation();

        reservation.release();

        assertEquals(InventoryReservationStatus.RELEASED, reservation.status());
    }

    @Test
    void confirmsReservation() {
        InventoryReservation reservation = reservation();

        reservation.confirm();

        assertEquals(InventoryReservationStatus.CONFIRMED, reservation.status());
    }

    @Test
    void expiresReservationWhenExpired() {
        InventoryReservation reservation = reservation();

        reservation.expire(expiresAt.plusSeconds(1));

        assertEquals(InventoryReservationStatus.EXPIRED, reservation.status());
    }

    @Test
    void rejectsExpireBeforeExpiresAt() {
        InventoryReservation reservation = reservation();

        assertThrows(DomainException.class, () -> reservation.expire(expiresAt.minusSeconds(1)));
    }

    private InventoryReservation reservation() {
        return InventoryReservation.hold(
                InventoryReservationId.newId(),
                OrderId.newId(),
                ProductId.newId(),
                1,
                expiresAt
        );
    }
}
