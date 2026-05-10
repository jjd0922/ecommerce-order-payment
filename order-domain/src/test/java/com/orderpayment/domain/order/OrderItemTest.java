package com.orderpayment.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.product.ProductId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderItemTest {

    @Test
    @DisplayName("subtotal 은 단가와 수량을 곱한 금액을 반환한다")
    void subtotal_whenCalled_thenReturnPriceMultipliedByQuantity() {
        OrderItem item = OrderItem.of(ProductId.newId(), "keyboard", Money.won(1000), 3);

        assertThat(item.subtotal()).isEqualTo(Money.won(3000));
    }

    @Test
    @DisplayName("of 는 수량이 0 이하면 예외를 던진다")
    void of_whenQuantityIsZero_thenThrowException() {
        assertThatThrownBy(() -> OrderItem.of(ProductId.newId(), "keyboard", Money.won(1000), 0))
                .isInstanceOf(DomainException.class);
    }
}
