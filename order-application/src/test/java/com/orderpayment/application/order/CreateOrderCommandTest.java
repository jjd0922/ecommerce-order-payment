package com.orderpayment.application.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.application.order.dto.CreateOrderCommand;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.product.ProductId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateOrderCommandTest {

    @Test
    @DisplayName("CreateOrderCommand 는 주문 라인이 비어 있으면 예외를 던진다")
    void constructor_whenOrderLinesEmpty_thenThrowException() {
        assertThatThrownBy(() -> new CreateOrderCommand(List.of()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("OrderLine 은 수량이 0 이하면 예외를 던진다")
    void orderLine_whenQuantityIsZero_thenThrowException() {
        assertThatThrownBy(() -> new CreateOrderCommand.OrderLine(ProductId.newId(), 0))
                .isInstanceOf(DomainException.class);
    }
}
