package com.orderpayment.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.product.ProductId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    @DisplayName("hold 는 가용 재고를 차감하고 보류 재고를 증가시킨다")
    void hold_whenQuantityAvailable_thenMoveQuantityToHeld() {
        Inventory inventory = Inventory.of(ProductId.newId(), 10);

        inventory.hold(3);

        assertThat(inventory.availableQuantity()).isEqualTo(7);
        assertThat(inventory.heldQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("release 는 보류 재고를 가용 재고로 되돌린다")
    void release_whenQuantityHeld_thenMoveQuantityToAvailable() {
        Inventory inventory = Inventory.of(ProductId.newId(), 10);
        inventory.hold(3);

        inventory.release(2);

        assertThat(inventory.availableQuantity()).isEqualTo(9);
        assertThat(inventory.heldQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("confirm 은 보류 재고를 확정해 보류 수량을 줄인다")
    void confirm_whenQuantityHeld_thenDecreaseHeldQuantity() {
        Inventory inventory = Inventory.of(ProductId.newId(), 10);
        inventory.hold(3);

        inventory.confirm(3);

        assertThat(inventory.availableQuantity()).isEqualTo(7);
        assertThat(inventory.heldQuantity()).isZero();
    }

    @Test
    @DisplayName("deduct 는 재고가 부족하면 예외를 던진다")
    void deduct_whenQuantityInsufficient_thenThrowException() {
        Inventory inventory = Inventory.of(ProductId.newId(), 2);

        assertThatThrownBy(() -> inventory.deduct(3))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("재고 생성과 보류는 유효하지 않은 수량이면 예외를 던진다")
    void inventoryOperation_whenInvalidQuantity_thenThrowException() {
        assertThatThrownBy(() -> Inventory.of(ProductId.newId(), -1))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Inventory.of(ProductId.newId(), 1).hold(0))
                .isInstanceOf(DomainException.class);
    }
}
