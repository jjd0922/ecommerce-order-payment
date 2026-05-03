package com.orderpayment.application.order;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.application.order.dto.CreateOrderCommand;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.product.ProductId;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateOrderCommandTest {

    @Test
    void rejectsEmptyOrderLines() {
        assertThrows(DomainException.class, () -> new CreateOrderCommand(List.of()));
    }

    @Test
    void rejectsInvalidQuantity() {
        assertThrows(DomainException.class, () -> new CreateOrderCommand.OrderLine(ProductId.newId(), 0));
    }
}
