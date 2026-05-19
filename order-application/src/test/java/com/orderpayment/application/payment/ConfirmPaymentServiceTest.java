package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.orderpayment.application.payment.service.ConfirmPaymentTransactionService;
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
import org.junit.jupiter.api.DisplayName;
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
    private final ConfirmPaymentTransactionService transactionService = new ConfirmPaymentTransactionService(
            paymentPort,
            paymentPort,
            orderPort,
            orderPort,
            inventoryReservationPort,
            inventoryReservationPort,
            () -> now,
            eventPublisher
    );
    private final ConfirmPaymentService service = new ConfirmPaymentService(paymentApprovalPort, transactionService);

    @Test
    @DisplayName("confirm 은 결제를 승인하고 재고 예약을 확정한다")
    void confirm_whenApprovalSucceeds_thenApprovePaymentAndConfirmReservation() {
        givenPreparedPayment();
        inventoryReservationPort.saveInventory(Inventory.restore(productId, 3, 2));

        ConfirmPaymentResult result = service.confirm(command());

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(inventoryReservationPort.inventory(productId).heldQuantity()).isZero();
        assertThat(inventoryReservationPort.firstReservation().status())
                .isEqualTo(InventoryReservationStatus.CONFIRMED);
        assertThat(eventPublisher.events).hasSize(2);
    }

    @Test
    @DisplayName("confirm 은 이미 승인된 결제이면 기존 결과를 반환한다")
    void confirm_whenPaymentAlreadyApproved_thenReturnExistingResult() {
        givenPreparedPayment();
        inventoryReservationPort.saveInventory(Inventory.restore(productId, 3, 2));

        ConfirmPaymentResult firstResult = service.confirm(command());
        ConfirmPaymentResult secondResult = service.confirm(command());

        assertThat(secondResult).isEqualTo(firstResult);
        assertThat(paymentApprovalPort.approveCount()).isEqualTo(1);
        assertThat(paymentPort.saveCount()).isEqualTo(1);
        assertThat(eventPublisher.events).hasSize(2);
    }

    @Test
    @DisplayName("confirm 은 결제 승인 실패 시 결제를 실패 처리하고 재고 예약을 해제한다")
    void confirm_whenApprovalFails_thenFailPaymentAndReleaseReservation() {
        givenPreparedPayment();
        inventoryReservationPort.saveInventory(Inventory.restore(productId, 3, 2));
        paymentApprovalPort.failNext("mock approval failed");

        ConfirmPaymentResult result = service.confirm(command());

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(inventoryReservationPort.inventory(productId).availableQuantity()).isEqualTo(5);
        assertThat(inventoryReservationPort.inventory(productId).heldQuantity()).isZero();
        assertThat(inventoryReservationPort.firstReservation().status())
                .isEqualTo(InventoryReservationStatus.RELEASED);
        assertThat(eventPublisher.events).hasSize(2);
    }

    @Test
    @DisplayName("confirm 은 다른 멱등키로 요청하면 예외를 던진다")
    void confirm_whenDifferentIdempotencyKey_thenThrowException() {
        givenPreparedPayment();

        assertThatThrownBy(() -> service.confirm(new ConfirmPaymentCommand(paymentId, new IdempotencyKey("another-key"))))
                .isInstanceOf(IdempotencyKeyConflictException.class);
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
