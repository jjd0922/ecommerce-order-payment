package com.orderpayment.application.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentQueryPort;
import com.orderpayment.application.payment.service.PreparePaymentService;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.inventory.Inventory;
import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.inventory.InventoryReservationId;
import com.orderpayment.domain.order.Order;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderItem;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.Payment;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;
import com.orderpayment.domain.product.ProductId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PreparePaymentServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 3, 21, 0);
    private final ProductId productId = ProductId.newId();
    private final OrderId orderId = OrderId.newId();
    private final PaymentId paymentId = new PaymentId(UUID.fromString("00000000-0000-0000-0000-000000000201"));
    private final FakeOrderPort orderPort = new FakeOrderPort();
    private final FakeInventoryReservationPort inventoryReservationPort = new FakeInventoryReservationPort();
    private final FakePaymentPort paymentPort = new FakePaymentPort();
    private final FakeDomainEventPublisher eventPublisher = new FakeDomainEventPublisher();
    private final PreparePaymentService service = new PreparePaymentService(
            orderPort,
            orderPort,
            inventoryReservationPort,
            paymentPort,
            paymentPort,
            () -> paymentId,
            () -> now,
            eventPublisher
    );

    @Test
    void preparesPaymentAndHoldsInventory() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 5));

        PreparePaymentResult result = service.prepare(command(orderId, "payment-request-1"));

        assertEquals(paymentId, result.paymentId());
        assertEquals(orderId, result.orderId());
        assertEquals(Money.won(2000), result.amount());
        assertEquals(OrderStatus.PAYMENT_PENDING, result.orderStatus());
        assertEquals(PaymentStatus.READY, result.paymentStatus());
        assertEquals(3, inventoryReservationPort.inventory(productId).availableQuantity());
        assertEquals(2, inventoryReservationPort.inventory(productId).heldQuantity());
        assertEquals(1, inventoryReservationPort.reservations.size());
        assertEquals(2, eventPublisher.events.size());
    }

    @Test
    void returnsExistingPaymentWhenSameIdempotencyKeyIsRequestedAgain() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 5));

        PreparePaymentResult firstResult = service.prepare(command(orderId, "payment-request-1"));
        PreparePaymentResult secondResult = service.prepare(command(orderId, "payment-request-1"));

        assertEquals(firstResult, secondResult);
        assertEquals(1, paymentPort.saveCount());
        assertEquals(1, inventoryReservationPort.reservations.size());
        assertEquals(2, eventPublisher.events.size());
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentOrder() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 5));
        service.prepare(command(orderId, "payment-request-1"));

        OrderId anotherOrderId = OrderId.newId();
        orderPort.saveOrder(Order.create(anotherOrderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 1)
        )));

        assertThrows(IdempotencyKeyConflictException.class, () -> service.prepare(
                command(anotherOrderId, "payment-request-1")
        ));
    }

    @Test
    void rejectsInsufficientInventory() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 1));

        assertThrows(DomainException.class, () -> service.prepare(command(orderId, "payment-request-1")));
    }

    private Order order() {
        return Order.create(orderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 2)
        ));
    }

    private static PreparePaymentCommand command(OrderId orderId, String idempotencyKey) {
        return new PreparePaymentCommand(orderId, new IdempotencyKey(idempotencyKey));
    }

    private static class FakeOrderPort implements OrderQueryPort, OrderCommandPort {

        private final Map<OrderId, Order> orders = new ConcurrentHashMap<>();

        @Override
        public Order getOrder(OrderId orderId) {
            Order order = orders.get(orderId);
            if (order == null) {
                throw new DomainException("order not found");
            }
            return order;
        }

        @Override
        public void saveOrder(Order order) {
            orders.put(order.id(), order);
        }
    }

    private static class FakeInventoryReservationPort implements InventoryReservationCommandPort {

        private final Map<ProductId, Inventory> inventories = new ConcurrentHashMap<>();
        private final Map<InventoryReservationId, InventoryReservation> reservations = new ConcurrentHashMap<>();

        @Override
        public InventoryReservation hold(OrderId orderId, ProductId productId, int quantity, LocalDateTime expiresAt) {
            Inventory inventory = inventories.get(productId);
            if (inventory == null) {
                throw new DomainException("inventory not found");
            }
            inventory.hold(quantity);

            InventoryReservation reservation = InventoryReservation.hold(
                    InventoryReservationId.newId(),
                    orderId,
                    productId,
                    quantity,
                    expiresAt
            );
            reservations.put(reservation.id(), reservation);
            return reservation;
        }

        @Override
        public void confirmAll(List<InventoryReservation> reservations) {
            reservations.forEach(InventoryReservation::confirm);
        }

        @Override
        public void releaseAll(List<InventoryReservation> reservations) {
            reservations.forEach(InventoryReservation::release);
        }

        void save(Inventory inventory) {
            inventories.put(inventory.productId(), inventory);
        }

        Inventory inventory(ProductId productId) {
            return inventories.get(productId);
        }
    }

    private static class FakePaymentPort implements PaymentQueryPort, PaymentCommandPort {

        private final Map<IdempotencyKey, Payment> payments = new ConcurrentHashMap<>();
        private final Map<PaymentId, Payment> paymentsById = new ConcurrentHashMap<>();
        private final AtomicInteger saveCount = new AtomicInteger();

        @Override
        public Payment getPayment(PaymentId paymentId) {
            Payment payment = paymentsById.get(paymentId);
            if (payment == null) {
                throw new DomainException("payment not found");
            }
            return payment;
        }

        @Override
        public Optional<Payment> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
            return Optional.ofNullable(payments.get(idempotencyKey));
        }

        @Override
        public void savePayment(Payment payment) {
            payments.put(payment.idempotencyKey(), payment);
            paymentsById.put(payment.id(), payment);
            saveCount.incrementAndGet();
        }

        int saveCount() {
            return saveCount.get();
        }
    }

    private static class FakeDomainEventPublisher implements DomainEventPublisherPort {

        private final List<DomainEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void publishAll(List<DomainEvent> events) {
            this.events.addAll(events);
        }
    }
}
