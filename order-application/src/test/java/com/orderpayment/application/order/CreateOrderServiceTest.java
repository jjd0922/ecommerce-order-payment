package com.orderpayment.application.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.application.order.dto.CreateOrderCommand;
import com.orderpayment.application.order.dto.CreateOrderResult;
import com.orderpayment.application.order.port.out.InventoryCommandPort;
import com.orderpayment.application.order.port.out.InventoryQueryPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.ProductQueryPort;
import com.orderpayment.application.order.service.CreateOrderService;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.inventory.Inventory;
import com.orderpayment.domain.order.Order;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.product.Product;
import com.orderpayment.domain.product.ProductId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateOrderServiceTest {

    private final ProductId keyboardId = ProductId.newId();
    private final ProductId mouseId = ProductId.newId();
    private final OrderId fixedOrderId = new OrderId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private final FakeProductPort productPort = new FakeProductPort();
    private final FakeInventoryPort inventoryPort = new FakeInventoryPort();
    private final FakeOrderRepository orderRepository = new FakeOrderRepository();
    private final CreateOrderService service = new CreateOrderService(
            productPort,
            inventoryPort,
            inventoryPort,
            orderRepository,
            () -> fixedOrderId
    );

    @Test
    void createsOrderAndDeductsInventory() {
        productPort.save(Product.selling(keyboardId, "keyboard", Money.won(1000)));
        productPort.save(Product.selling(mouseId, "mouse", Money.won(500)));
        inventoryPort.save(Inventory.of(keyboardId, 10));
        inventoryPort.save(Inventory.of(mouseId, 5));

        CreateOrderResult result = service.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.OrderLine(keyboardId, 2),
                new CreateOrderCommand.OrderLine(mouseId, 1)
        )));

        assertEquals(fixedOrderId, result.orderId());
        assertEquals(Money.won(2500), result.totalAmount());
        assertEquals(OrderStatus.CREATED, result.status());
        assertEquals(8, inventoryPort.getInventory(keyboardId).quantity());
        assertEquals(4, inventoryPort.getInventory(mouseId).quantity());
        assertEquals(Money.won(2500), orderRepository.savedOrder.totalAmount());
    }

    @Test
    void rejectsStoppedProduct() {
        Product product = Product.selling(keyboardId, "keyboard", Money.won(1000));
        product.stopSelling();
        productPort.save(product);
        inventoryPort.save(Inventory.of(keyboardId, 10));

        CreateOrderCommand command = new CreateOrderCommand(List.of(
                new CreateOrderCommand.OrderLine(keyboardId, 1)
        ));

        assertThrows(DomainException.class, () -> service.create(command));
    }

    @Test
    void rejectsInsufficientInventory() {
        productPort.save(Product.selling(keyboardId, "keyboard", Money.won(1000)));
        inventoryPort.save(Inventory.of(keyboardId, 1));

        CreateOrderCommand command = new CreateOrderCommand(List.of(
                new CreateOrderCommand.OrderLine(keyboardId, 2)
        ));

        assertThrows(DomainException.class, () -> service.create(command));
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

    private static class FakeInventoryPort implements InventoryQueryPort, InventoryCommandPort {

        private final Map<ProductId, Inventory> inventories = new HashMap<>();

        @Override
        public Inventory getInventory(ProductId productId) {
            Inventory inventory = inventories.get(productId);
            if (inventory == null) {
                throw new DomainException("inventory not found");
            }
            return inventory;
        }

        @Override
        public void saveInventory(Inventory inventory) {
            inventories.put(inventory.productId(), inventory);
        }

        void save(Inventory inventory) {
            saveInventory(inventory);
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
