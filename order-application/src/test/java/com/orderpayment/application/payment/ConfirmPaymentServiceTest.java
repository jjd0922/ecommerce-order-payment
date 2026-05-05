package com.orderpayment.application.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.InventoryReservationQueryPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentQueryPort;
import com.orderpayment.application.payment.service.ConfirmPaymentService;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.inventory.Inventory;
import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.inventory.InventoryReservationId;
import com.orderpayment.domain.inventory.InventoryReservationStatus;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConfirmPaymentServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 4, 10, 0);
    private final ProductId productId = ProductId.newId();
    private final OrderId orderId = OrderId.newId();
    private final PaymentId paymentId = new PaymentId(UUID.fromString("00000000-0000-0000-0000-000000000301"));
    private final IdempotencyKey idempotencyKey = new IdempotencyKey("payment-request-1");
    private final FakeOrderPort orderPort = new FakeOrderPort();
    private final FakeInventoryReservationPort inventoryReservationPort = new FakeInventoryReservationPort();
    private final FakePaymentPort paymentPort = new FakePaymentPort();
    private final FakePaymentApprovalPort paymentApprovalPort = new FakePaymentApprovalPort();
    private final FakeDomainEventPublisher eventPublisher = new FakeDomainEventPublisher();
    private final ConfirmPaymentService service = new ConfirmPaymentService(
            paymentPort,
            paymentPort,
            orderPort,
            orderPort,
            inventoryReservationPort,
            inventoryReservationPort,
            paymentApprovalPort,
            () -> now,
            eventPublisher
    );

    @Test
    void approvesPaymentAndConfirmsInventoryReservation() {
        givenPreparedPayment();
        inventoryReservationPort.saveInventory(Inventory.restore(productId, 3, 2));

        ConfirmPaymentResult result = service.confirm(command());

        assertEquals(paymentId, result.paymentId());
        assertEquals(OrderStatus.PAID, result.orderStatus());
        assertEquals(PaymentStatus.APPROVED, result.paymentStatus());
        assertEquals(0, inventoryReservationPort.inventory(productId).heldQuantity());
        assertEquals(InventoryReservationStatus.CONFIRMED, inventoryReservationPort.firstReservation().status());
        assertEquals(2, eventPublisher.events.size());
    }

    @Test
    void returnsExistingResultWhenAlreadyApproved() {
        givenPreparedPayment();
        inventoryReservationPort.saveInventory(Inventory.restore(productId, 3, 2));

        ConfirmPaymentResult firstResult = service.confirm(command());
        ConfirmPaymentResult secondResult = service.confirm(command());

        assertEquals(firstResult, secondResult);
        assertEquals(1, paymentApprovalPort.approveCount());
        assertEquals(1, paymentPort.saveCount());
        assertEquals(2, eventPublisher.events.size());
    }

    @Test
    void failsPaymentAndReleasesInventoryReservationWhenMockApprovalFails() {
        givenPreparedPayment();
        inventoryReservationPort.saveInventory(Inventory.restore(productId, 3, 2));
        paymentApprovalPort.failNext("mock approval failed");

        ConfirmPaymentResult result = service.confirm(command());

        assertEquals(OrderStatus.FAILED, result.orderStatus());
        assertEquals(PaymentStatus.FAILED, result.paymentStatus());
        assertEquals(5, inventoryReservationPort.inventory(productId).availableQuantity());
        assertEquals(0, inventoryReservationPort.inventory(productId).heldQuantity());
        assertEquals(InventoryReservationStatus.RELEASED, inventoryReservationPort.firstReservation().status());
        assertEquals(2, eventPublisher.events.size());
    }

    @Test
    void rejectsDifferentIdempotencyKey() {
        givenPreparedPayment();

        assertThrows(IdempotencyKeyConflictException.class, () -> service.confirm(new ConfirmPaymentCommand(
                paymentId,
                new IdempotencyKey("another-key")
        )));
    }

    private void givenPreparedPayment() {
        Order order = Order.create(orderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 2)
        ));
        order.requestPayment();
        orderPort.saveOrder(order);

        Payment payment = Payment.ready(paymentId, orderId, Money.won(2000), idempotencyKey);
        paymentPort.savePaymentWithoutCounting(payment);

        InventoryReservation reservation = InventoryReservation.hold(
                InventoryReservationId.newId(),
                orderId,
                productId,
                2,
                now.plusMinutes(10)
        );
        inventoryReservationPort.saveReservation(reservation);
    }

    private ConfirmPaymentCommand command() {
        return new ConfirmPaymentCommand(paymentId, idempotencyKey);
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

    private static class FakePaymentPort implements PaymentQueryPort, PaymentCommandPort {

        private final Map<PaymentId, Payment> paymentsById = new ConcurrentHashMap<>();
        private final Map<IdempotencyKey, Payment> paymentsByKey = new ConcurrentHashMap<>();
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
            return Optional.ofNullable(paymentsByKey.get(idempotencyKey));
        }

        @Override
        public void savePayment(Payment payment) {
            savePaymentWithoutCounting(payment);
            saveCount.incrementAndGet();
        }

        void savePaymentWithoutCounting(Payment payment) {
            paymentsById.put(payment.id(), payment);
            paymentsByKey.put(payment.idempotencyKey(), payment);
        }

        int saveCount() {
            return saveCount.get();
        }
    }

    private static class FakeInventoryReservationPort
            implements InventoryReservationQueryPort, InventoryReservationCommandPort {

        private final Map<ProductId, Inventory> inventories = new ConcurrentHashMap<>();
        private final Map<InventoryReservationId, InventoryReservation> reservations = new ConcurrentHashMap<>();

        @Override
        public InventoryReservation hold(OrderId orderId, ProductId productId, int quantity, LocalDateTime expiresAt) {
            Inventory inventory = inventories.get(productId);
            inventory.hold(quantity);
            InventoryReservation reservation = InventoryReservation.hold(
                    InventoryReservationId.newId(),
                    orderId,
                    productId,
                    quantity,
                    expiresAt
            );
            saveReservation(reservation);
            return reservation;
        }

        @Override
        public List<InventoryReservation> findHeldReservationsByOrderId(OrderId orderId) {
            return reservations.values().stream()
                    .filter(reservation -> reservation.orderId().equals(orderId))
                    .filter(reservation -> reservation.status() == InventoryReservationStatus.HELD)
                    .toList();
        }

        @Override
        public void confirmAll(List<InventoryReservation> reservations) {
            for (InventoryReservation reservation : reservations) {
                Inventory inventory = inventories.get(reservation.productId());
                inventory.confirm(reservation.quantity());
            }
        }

        @Override
        public void releaseAll(List<InventoryReservation> reservations) {
            for (InventoryReservation reservation : reservations) {
                Inventory inventory = inventories.get(reservation.productId());
                inventory.release(reservation.quantity());
            }
        }

        void saveInventory(Inventory inventory) {
            inventories.put(inventory.productId(), inventory);
        }

        void saveReservation(InventoryReservation reservation) {
            reservations.put(reservation.id(), reservation);
        }

        Inventory inventory(ProductId productId) {
            return inventories.get(productId);
        }

        InventoryReservation firstReservation() {
            return reservations.values().iterator().next();
        }
    }

    private static class FakePaymentApprovalPort implements PaymentApprovalPort {

        private final AtomicInteger approveCount = new AtomicInteger();
        private String failureReason;

        @Override
        public PaymentApprovalResult approve(Payment payment) {
            approveCount.incrementAndGet();
            if (failureReason != null) {
                return PaymentApprovalResult.failed(failureReason);
            }
            return PaymentApprovalResult.approved();
        }

        void failNext(String failureReason) {
            this.failureReason = failureReason;
        }

        int approveCount() {
            return approveCount.get();
        }
    }

    private static class FakeDomainEventPublisher implements DomainEventPublisherPort {

        private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publishAll(List<DomainEvent> events) {
            this.events.addAll(events);
        }
    }
}
