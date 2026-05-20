package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
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
import com.orderpayment.application.payment.service.ConfirmPaymentIdempotencyHandler;
import com.orderpayment.application.payment.service.ConfirmPaymentIdempotencyResponseSerializer;
import com.orderpayment.application.payment.service.ConfirmPaymentRequestHashService;
import com.orderpayment.application.payment.service.ConfirmPaymentService;
import com.orderpayment.application.payment.service.ConfirmPaymentTransactionService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    private final FakeIdempotencyRecordPort idempotencyRecordPort = new FakeIdempotencyRecordPort();
    private final FakePaymentApprovalPort paymentApprovalPort = new FakePaymentApprovalPort();
    private final FakeDomainEventPublisher eventPublisher = new FakeDomainEventPublisher();
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
    private final ConfirmPaymentService service = new ConfirmPaymentService(
            paymentApprovalPort,
            transactionService,
            new SimpleMeterRegistry()
    );

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
        assertThat(paymentPort.saveCount()).isEqualTo(2);
        assertThat(eventPublisher.events).hasSize(2);
    }

    @Test
    @DisplayName("confirm calls payment approval only once for duplicate concurrent requests")
    void confirm_whenDuplicateConcurrentRequests_thenApproveOnlyOnce() throws Exception {
        givenPreparedPayment();
        inventoryReservationPort.saveInventory(Inventory.restore(productId, 3, 2));
        int requestCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch approvalStartedLatch = new CountDownLatch(1);
        CountDownLatch releaseApprovalLatch = new CountDownLatch(1);
        paymentApprovalPort.blockApproval(approvalStartedLatch, releaseApprovalLatch);

        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                await(startLatch);
                service.confirm(command());
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(approvalStartedLatch.await(5, TimeUnit.SECONDS)).isTrue();
        awaitUntilPaymentProcessing();
        releaseApprovalLatch.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(paymentApprovalPort.approveCount()).isEqualTo(1);
        assertThat(paymentPort.saveCount()).isEqualTo(2);
        assertThat(paymentPort.getPayment(paymentId).status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(orderPort.getOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(inventoryReservationPort.firstReservation().status())
                .isEqualTo(InventoryReservationStatus.CONFIRMED);
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
    @DisplayName("confirm accepts idempotency key independent from prepare key")
    void confirm_whenConfirmIdempotencyKeyDiffersFromPrepareKey_thenApprovePayment() {
        givenPreparedPayment();
        inventoryReservationPort.saveInventory(Inventory.restore(productId, 3, 2));

        ConfirmPaymentResult result = service.confirm(new ConfirmPaymentCommand(paymentId, new IdempotencyKey("another-key")));

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(paymentApprovalPort.approveCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("confirm throws when payment is already processing")
    void confirm_whenPaymentAlreadyProcessing_thenThrowException() {
        givenPreparedPayment();
        Payment payment = paymentPort.getPayment(paymentId);
        payment.startApproval(now.minusMinutes(1));
        paymentPort.savePaymentWithoutCounting(payment);

        assertThatThrownBy(() -> service.confirm(command()))
                .isInstanceOf(PaymentInProgressException.class);
        assertThat(paymentApprovalPort.approveCount()).isZero();
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

    private void awaitUntilPaymentProcessing() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (paymentPort.getPayment(paymentId).status() == PaymentStatus.PROCESSING) {
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("payment did not enter PROCESSING status");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
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
        private final AtomicInteger forUpdateReadCount = new AtomicInteger();

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
            int readCount = forUpdateReadCount.incrementAndGet();
            if (readCount > 1) {
                awaitUntilPaymentStatusChangesFromReady(paymentId);
            }
            return getPayment(paymentId);
        }

        @Override
        public Optional<Payment> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
            return Optional.ofNullable(paymentsByKey.get(idempotencyKey));
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

        private void awaitUntilPaymentStatusChangesFromReady(PaymentId paymentId) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                if (getPayment(paymentId).status() != PaymentStatus.READY) {
                    return;
                }
                Thread.yield();
            }
            throw new AssertionError("payment row lock was not released");
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
        private CountDownLatch approvalStartedLatch;
        private CountDownLatch releaseApprovalLatch;

        @Override
        public PaymentApprovalResult approve(Payment payment) {
            approveCount.incrementAndGet();
            if (approvalStartedLatch != null && releaseApprovalLatch != null) {
                approvalStartedLatch.countDown();
                await(releaseApprovalLatch);
            }
            if (failureReason != null) {
            return PaymentApprovalResult.failed("mock-pg-" + payment.id().value(), failureReason);
        }
            return PaymentApprovalResult.approved("mock-pg-" + payment.id().value());
        }

        void failNext(String failureReason) {
            this.failureReason = failureReason;
        }

        void blockApproval(CountDownLatch approvalStartedLatch, CountDownLatch releaseApprovalLatch) {
            this.approvalStartedLatch = approvalStartedLatch;
            this.releaseApprovalLatch = releaseApprovalLatch;
        }

        int approveCount() {
            return approveCount.get();
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

    private static class FakeDomainEventPublisher implements DomainEventPublisherPort {

        private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publishAll(List<DomainEvent> events) {
            this.events.addAll(events);
        }
    }
}
