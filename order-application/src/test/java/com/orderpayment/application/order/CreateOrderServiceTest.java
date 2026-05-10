package com.orderpayment.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateOrderServiceTest {

    private final ProductId keyboardId = ProductId.newId();
    private final ProductId mouseId = ProductId.newId();
    private final OrderId fixedOrderId = new OrderId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private final FakeProductPort productPort = new FakeProductPort();
    private final FakeOrderRepository orderRepository = new FakeOrderRepository();
    private final CreateOrderService service = new CreateOrderService(
            productPort,
            orderRepository,
            () -> fixedOrderId
    );

    @Test
    @DisplayName("create 는 상품을 조회해 주문을 저장하고 결과를 반환한다")
    void create_whenProductsSelling_thenSaveOrderAndReturnResult() {
        productPort.save(Product.selling(keyboardId, "keyboard", Money.won(1000)));
        productPort.save(Product.selling(mouseId, "mouse", Money.won(500)));

        CreateOrderResult result = service.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.OrderLine(keyboardId, 2),
                new CreateOrderCommand.OrderLine(mouseId, 1)
        )));

        assertThat(result.orderId()).isEqualTo(fixedOrderId);
        assertThat(result.totalAmount()).isEqualTo(Money.won(2500));
        assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(orderRepository.savedOrder.totalAmount()).isEqualTo(Money.won(2500));
    }

    @Test
    @DisplayName("create 는 판매 중지 상품이면 예외를 던진다")
    void create_whenProductStopped_thenThrowException() {
        Product product = Product.selling(keyboardId, "keyboard", Money.won(1000));
        product.stopSelling();
        productPort.save(product);

        CreateOrderCommand command = new CreateOrderCommand(List.of(
                new CreateOrderCommand.OrderLine(keyboardId, 1)
        ));

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(DomainException.class);
    }

    private static class FakeProductPort implements ProductQueryPort {

        private final Map<ProductId, Product> products = new HashMap<>();

        @Override
        public Product getProduct(ProductId productId) {
            Product product = products.get(productId);
            if (product == null) {
                throw new DomainException("product not found");
            }
            return product;
        }

        void save(Product product) {
            products.put(product.id(), product);
        }
    }

    private static class FakeOrderRepository implements OrderCommandPort {

        private Order savedOrder;

        @Override
        public void saveOrder(Order order) {
            this.savedOrder = order;
        }
    }
}
