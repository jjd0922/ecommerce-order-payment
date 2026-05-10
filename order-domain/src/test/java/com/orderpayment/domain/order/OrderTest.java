package com.orderpayment.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.product.ProductId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    @DisplayName("create 는 주문을 생성하고 총 주문 금액을 계산한다")
    void create_whenItemsGiven_thenCreateOrderAndCalculateTotalAmount() {
        Order order = Order.create(OrderId.newId(), List.of(
                OrderItem.of(ProductId.newId(), "keyboard", Money.won(1000), 2),
                OrderItem.of(ProductId.newId(), "mouse", Money.won(500), 1)
        ));

        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.totalAmount()).isEqualTo(Money.won(2500));
    }

    @Test
    @DisplayName("markPaid 는 결제 요청된 주문을 PAID 상태로 변경한다")
    void markPaid_whenPaymentRequested_thenChangeStatusToPaid() {
        Order order = order();

        order.requestPayment();
        order.markPaid();

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("markPaid 는 결제 요청 전이면 예외를 던진다")
    void markPaid_whenPaymentNotRequested_thenThrowException() {
        Order order = order();

        assertThatThrownBy(order::markPaid)
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("cancel 은 결제 완료 주문이면 예외를 던진다")
    void cancel_whenOrderPaid_thenThrowException() {
        Order order = order();
        order.requestPayment();
        order.markPaid();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 는 주문 항목이 비어 있으면 예외를 던진다")
    void create_whenItemsEmpty_thenThrowException() {
        assertThatThrownBy(() -> Order.create(OrderId.newId(), List.of()))
                .isInstanceOf(DomainException.class);
    }

    private static Order order() {
        return Order.create(OrderId.newId(), List.of(
                OrderItem.of(ProductId.newId(), "keyboard", Money.won(1000), 1)
        ));
    }
}
