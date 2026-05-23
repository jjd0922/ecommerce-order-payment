package com.orderpayment.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderpayment.application.order.dto.CreateOrderCommand;
import com.orderpayment.application.order.dto.CreateOrderResult;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.ProductQueryPort;
import com.orderpayment.application.order.service.CreateOrderService;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.Order;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.product.Product;
import com.orderpayment.domain.product.ProductId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    private final ProductId keyboardId = ProductId.newId();
    private final ProductId mouseId = ProductId.newId();
    private final OrderId fixedOrderId = new OrderId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Mock
    private ProductQueryPort productQueryPort;

    @Mock
    private OrderCommandPort orderCommandPort;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private CreateOrderService service;

    @BeforeEach
    void setUp() {
        service = new CreateOrderService(
                productQueryPort,
                orderCommandPort,
                () -> fixedOrderId
        );
    }

    @Test
    @DisplayName("create 는 판매 중인 상품으로 주문을 저장하고 결과를 반환한다")
    void create_whenProductsSelling_thenSaveOrderAndReturnResult() {
        when(productQueryPort.getProduct(keyboardId))
                .thenReturn(Product.selling(keyboardId, "keyboard", Money.won(1000)));
        when(productQueryPort.getProduct(mouseId))
                .thenReturn(Product.selling(mouseId, "mouse", Money.won(500)));

        CreateOrderResult result = service.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.OrderLine(keyboardId, 2),
                new CreateOrderCommand.OrderLine(mouseId, 1)
        )));

        assertThat(result.orderId()).isEqualTo(fixedOrderId);
        assertThat(result.totalAmount()).isEqualTo(Money.won(2500));
        assertThat(result.status()).isEqualTo(OrderStatus.CREATED);

        verify(orderCommandPort).saveOrder(orderCaptor.capture());
        assertThat(orderCaptor.getValue().totalAmount()).isEqualTo(Money.won(2500));
    }

    @Test
    @DisplayName("create 는 판매 중지 상품이면 예외를 던진다")
    void create_whenProductStopped_thenThrowException() {
        Product product = Product.selling(keyboardId, "keyboard", Money.won(1000));
        product.stopSelling();
        when(productQueryPort.getProduct(keyboardId)).thenReturn(product);

        CreateOrderCommand command = new CreateOrderCommand(List.of(
                new CreateOrderCommand.OrderLine(keyboardId, 1)
        ));

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(DomainException.class);
    }
}
