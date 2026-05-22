package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.payment.dto.RecoverProcessingPaymentsResult;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.InventoryReservationQueryPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalQueryPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentQueryPort;
import com.orderpayment.application.payment.service.ConfirmPaymentIdempotencyHandler;
import com.orderpayment.application.payment.service.ConfirmPaymentIdempotencyResponseSerializer;
import com.orderpayment.application.payment.service.ConfirmPaymentRequestHashService;
import com.orderpayment.application.payment.service.ConfirmPaymentTransactionService;
import com.orderpayment.application.payment.service.RecoverProcessingPaymentsService;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecoverProcessingPaymentsServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 20, 10, 0);
    private final ProductId productId = ProductId.newId();
    private final OrderId orderId = OrderId.newId();
    private final PaymentId paymentId = new PaymentId(UUID.fromString("00000000-0000-0000-0000-000000000901"));
    private final IdempotencyKey idempotencyKey = new IdempotencyKey("payment-request-1");
    private final FakeOrderPort orderPort = new FakeOrderPort();
    private final FakePaymentPort paymentPort = new FakePaymentPort();
    private final FakeInventoryReservationPort inventoryReservationPort = new FakeInventoryReservationPort();
    private final FakePaymentApprovalQueryPort paymentApprovalQueryPort = new FakePaymentApprovalQueryPort();
    private final FakeDomainEventPublisher eventPublisher = new FakeDomainEventPublisher();
    private final FakeIdempotencyRecordPort idempotencyRecordPort = new FakeIdempotencyRecordPort();
    private final ConfirmPaymentIdempotencyHandler idempotencyHandler = new ConfirmPaymentIdempotencyHandler(
            idempotencyRecordPort,
            idempotencyRecordPort,
            () -> now,
            new ConfirmPaymentRequestHashService(),
            new ConfirmPaymentIdempotencyResponseSerializer(new ObjectMapper())
    );
    private final ConfirmPaymentTransactionService transactionService = new ConfirmPaymentTransactionService(
            paymentPort,
            paymentPort,
            orderPort,
            orderPort,
            inventoryReservationPort,
            inventoryReservationPort,
            () -> now,
            eventPublisher,
            idempotencyHandler
    );
    private final RecoverProcessingPaymentsService service = new RecoverProcessingPaymentsService(
            paymentPort,
            paymentApprovalQueryPort,
            transactionService,
            () -> now,
            Duration.ofMinutes(5),
            100
    );

    @Test
    @DisplayName("recover approves timed out processing payment when PG result is approved")
    void recover_whenProcessingPaymentApprovedByPg_thenApprovePaymentAndConfirmReservation() {
        givenProcessingPaymentTimedOut();
        paymentApprovalQueryPort.save(paymentId, PaymentApprovalResult.approved("pg-transaction-1"));

        RecoverProcessingPaymentsResult result = service.recover();

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(paymentPort.getPayment(paymentId).status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(paymentPort.getPayment(paymentId).pgTransactionId()).isEqualTo("pg-transaction-1");
        assertThat(orderPort.getOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(inventoryReservationPort.firstReservation().status())
                .isEqualTo(InventoryReservationStatus.CONFIRMED);
        assertThat(inventoryReservationPort.inventory(productId).heldQuantity()).isZero();
        assertThat(eventPublisher.events).hasSize(2);
    }

    @Test
    @DisplayName("recover fails timed out processing payment when PG result is failed")
    void recover_whenProcessingPaymentFailedByPg_thenFailPaymentAndReleaseReservation() {
        givenProcessingPaymentTimedOut();
        paymentApprovalQueryPort.save(
                paymentId,
                PaymentApprovalResult.failed("pg-transaction-2", "mock approval failed")
        );

        RecoverProcessingPaymentsResult result = service.recover();

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(paymentPort.getPayment(paymentId).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(paymentPort.getPayment(paymentId).pgTransactionId()).isEqualTo("pg-transaction-2");
        assertThat(orderPort.getOrder(orderId).status()).isEqualTo(OrderStatus.FAILED);
        assertThat(inventoryReservationPort.firstReservation().status())
                .isEqualTo(InventoryReservationStatus.RELEASED);
        assertThat(inventoryReservationPort.inventory(productId).availableQuantity()).isEqualTo(5);
        assertThat(eventPublisher.events).hasSize(2);
    }

    @Test
    @DisplayName("recover leaves processing payment when PG result is unknown")
    void recover_whenPgResultUnknown_thenLeavePaymentProcessing() {
        givenProcessingPaymentTimedOut();

        RecoverProcessingPaymentsResult result = service.recover();

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isZero();
        assertThat(paymentPort.getPayment(paymentId).status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(orderPort.getOrder(orderId).status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(inventoryReservationPort.firstReservation().status()).isEqualTo(InventoryReservationStatus.HELD);
        assertThat(eventPublisher.events).isEmpty();
    }

    private void givenProcessingPaymentTimedOut() {
        Order order = Order.create(orderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 2)
        ));
        order.requestPayment();
        orderPort.saveOrder(order);

        Payment payment = Payment.ready(paymentId, orderId, Money.won(2000), idempotencyKey);
        payment.startApproval(now.minusMinutes(10));
        paymentPort.savePayment(payment);

        InventoryReservation reservation = InventoryReservation.hold(
                InventoryReservationId.newId(),
                orderId,
                productId,
                2,
                now.plusMinutes(10)
        );
        inventoryReservationPort.saveInventory(Inventory.restore(productId, 3, 2));
        inventoryReservationPort.saveReservation(reservation);
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

        @Override
        public Payment getPayment(PaymentId paymentId) {
            Payment payment = paymentsById.get(paymentId);
            if (payment == null) {
                throw new DomainException("payment not found");
            }
            return payment;
        }

        @Override
        public Payment getPaymentForUpdate(PaymentId paymentId) {
            return getPayment(paymentId);
        }

        @Override
        public Optional<Payment> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
            return paymentsById.values().stream()
                    .filter(payment -> payment.idempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public List<Payment> findProcessingPaymentsRequestedBefore(LocalDateTime requestedBefore, int limit) {
            return paymentsById.values().stream()
                    .filter(payment -> payment.status() == PaymentStatus.PROCESSING)
                    .filter(payment -> payment.approvalRequestedAt() != null)
                    .filter(payment -> payment.approvalRequestedAt().isBefore(requestedBefore))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void savePayment(Payment payment) {
            paymentsById.put(payment.id(), payment);
        }
    }

    private static class FakeInventoryReservationPort
            implements InventoryReservationQueryPort, InventoryReservationCommandPort {

        private final Map<ProductId, Inventory> inventories = new ConcurrentHashMap<>();
        private final Map<InventoryReservationId, InventoryReservation> reservations = new ConcurrentHashMap<>();

        @Override
        public InventoryReservation hold(OrderId orderId, ProductId productId, int quantity, LocalDateTime expiresAt) {
            throw new UnsupportedOperationException("not used");
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
                inventories.get(reservation.productId()).confirm(reservation.quantity());
            }
        }

        @Override
        public void releaseAll(List<InventoryReservation> reservations) {
            for (InventoryReservation reservation : reservations) {
                inventories.get(reservation.productId()).release(reservation.quantity());
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

    private static class FakePaymentApprovalQueryPort implements PaymentApprovalQueryPort {

        private final Map<PaymentId, PaymentApprovalResult> results = new ConcurrentHashMap<>();

        @Override
        public Optional<PaymentApprovalResult> findApprovalResult(Payment payment) {
            return Optional.ofNullable(results.get(payment.id()));
        }

        void save(PaymentId paymentId, PaymentApprovalResult result) {
            results.put(paymentId, result);
        }
    }

    private static class FakeDomainEventPublisher implements DomainEventPublisherPort {

        private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publishAll(List<DomainEvent> events) {
            this.events.addAll(events);
        }
    }

    private static class FakeIdempotencyRecordPort
            implements IdempotencyRecordQueryPort, IdempotencyRecordCommandPort {

        private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

        @Override
        public Optional<IdempotencyRecord> findByKey(String key) {
            return Optional.ofNullable(records.get(key));
        }

        @Override
        public void save(IdempotencyRecord record) {
            records.put(record.key(), record);
        }
    }
}
