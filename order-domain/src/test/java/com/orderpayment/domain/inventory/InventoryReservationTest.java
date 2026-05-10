package com.orderpayment.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryReservationTest {

    private final LocalDateTime expiresAt = LocalDateTime.of(2026, 5, 3, 21, 0);

    @Test
    @DisplayName("hold 는 HELD 상태의 재고 예약을 생성한다")
    void hold_whenCalled_thenCreateHeldReservation() {
        InventoryReservation reservation = reservation();

        assertThat(reservation.status()).isEqualTo(InventoryReservationStatus.HELD);
    }

    @Test
    @DisplayName("release 는 재고 예약 상태를 RELEASED 로 변경한다")
    void release_whenHeld_thenChangeStatusToReleased() {
        InventoryReservation reservation = reservation();

        reservation.release();

        assertThat(reservation.status()).isEqualTo(InventoryReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("confirm 은 재고 예약 상태를 CONFIRMED 로 변경한다")
    void confirm_whenHeld_thenChangeStatusToConfirmed() {
        InventoryReservation reservation = reservation();

        reservation.confirm();

        assertThat(reservation.status()).isEqualTo(InventoryReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("expire 는 만료 시간이 지나면 상태를 EXPIRED 로 변경한다")
    void expire_whenExpired_thenChangeStatusToExpired() {
        InventoryReservation reservation = reservation();

        reservation.expire(expiresAt.plusSeconds(1));

        assertThat(reservation.status()).isEqualTo(InventoryReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("expire 는 만료 전이면 예외를 던진다")
    void expire_whenNotExpired_thenThrowException() {
        InventoryReservation reservation = reservation();

        assertThatThrownBy(() -> reservation.expire(expiresAt.minusSeconds(1)))
                .isInstanceOf(DomainException.class);
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
