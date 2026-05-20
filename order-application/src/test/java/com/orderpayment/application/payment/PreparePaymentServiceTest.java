package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.idempotency.IdempotencyInFlightException;
import com.orderpayment.application.idempotency.IdempotencyRecordAlreadyExistsException;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentQueryPort;
import com.orderpayment.application.payment.service.PreparePaymentService;
import com.orderpayment.application.payment.service.PreparePaymentIdempotencyHandler;
import com.orderpayment.application.payment.service.PreparePaymentRequestHashService;
import com.orderpayment.application.payment.service.PreparePaymentIdempotencyResponseSerializer;
import com.orderpayment.application.payment.service.PreparePaymentTransactionService;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreparePaymentServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 3, 21, 0);
    private final ProductId productId = ProductId.newId();
    private final OrderId orderId = OrderId.newId();
    private final PaymentId paymentId = new PaymentId(UUID.fromString("00000000-0000-0000-0000-000000000201"));
    private final FakeOrderPort orderPort = new FakeOrderPort();
    private final FakeInventoryReservationPort inventoryReservationPort = new FakeInventoryReservationPort();
    private final FakePaymentPort paymentPort = new FakePaymentPort();
    private final FakeIdempotencyRecordPort idempotencyRecordPort = new FakeIdempotencyRecordPort();
    private final FakeDomainEventPublisher eventPublisher = new FakeDomainEventPublisher();
    private final PreparePaymentRequestHashService requestHashService = new PreparePaymentRequestHashService();
    private final PreparePaymentIdempotencyResponseSerializer responseSerializer =
            new PreparePaymentIdempotencyResponseSerializer(new ObjectMapper());
    private final PreparePaymentIdempotencyHandler idempotencyHandler = new PreparePaymentIdempotencyHandler(
            idempotencyRecordPort,
            idempotencyRecordPort,
            () -> now,
            requestHashService,
            responseSerializer
    );
    private final PreparePaymentTransactionService transactionService = new PreparePaymentTransactionService(
            orderPort,
            orderPort,
            inventoryReservationPort,
            paymentPort,
            () -> paymentId,
            () -> now,
            eventPublisher,
            idempotencyHandler
    );
    private final PreparePaymentService service = new PreparePaymentService(transactionService);

    @Test
    @DisplayName("prepare 는 결제를 준비하고 재고를 보류한다")
    void prepare_whenOrderExistsAndInventoryAvailable_thenPreparePaymentAndHoldInventory() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 5));

        PreparePaymentResult result = service.prepare(command(orderId, "payment-request-1"));

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.amount()).isEqualTo(Money.won(2000));
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(inventoryReservationPort.inventory(productId).availableQuantity()).isEqualTo(3);
        assertThat(inventoryReservationPort.inventory(productId).heldQuantity()).isEqualTo(2);
        assertThat(inventoryReservationPort.reservations).hasSize(1);
        assertThat(eventPublisher.events).hasSize(2);
    }

    @Test
    @DisplayName("prepare 는 같은 멱등키 요청이면 기존 결제 결과를 반환한다")
    void prepare_whenSameIdempotencyKeyRequestedAgain_thenReturnExistingPaymentResult() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 5));

        PreparePaymentResult firstResult = service.prepare(command(orderId, "payment-request-1"));
        PreparePaymentResult secondResult = service.prepare(command(orderId, "payment-request-1"));

        assertThat(secondResult).isEqualTo(firstResult);
        assertThat(paymentPort.saveCount()).isEqualTo(1);
        assertThat(inventoryReservationPort.reservations).hasSize(1);
        assertThat(eventPublisher.events).hasSize(2);
    }

    @Test
    @DisplayName("prepare returns existing result when idempotency record insert conflicts")
    void prepare_whenIdempotencyRecordAlreadyExists_thenReplayExistingResult() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 5));
        idempotencyRecordPort.failNextSaveWithExistingRecord(completedRecord(
                "payment-request-1",
                orderId,
                paymentId
        ));

        PreparePaymentResult result = service.prepare(command(orderId, "payment-request-1"));

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.amount()).isEqualTo(Money.won(2000));
        assertThat(paymentPort.saveCount()).isZero();
        assertThat(inventoryReservationPort.reservations).isEmpty();
        assertThat(eventPublisher.events).isEmpty();
    }

    @Test
    @DisplayName("prepare throws when same idempotency key is in flight")
    void prepare_whenSameIdempotencyKeyIsInFlight_thenThrowException() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 5));
        idempotencyRecordPort.save(IdempotencyRecord.inFlight(
                "payment-request-1",
                requestHashService.hash(command(orderId, "payment-request-1")),
                now,
                now.plusDays(1)
        ));

        assertThatThrownBy(() -> service.prepare(command(orderId, "payment-request-1")))
                .isInstanceOf(IdempotencyInFlightException.class);
        assertThat(paymentPort.saveCount()).isZero();
        assertThat(inventoryReservationPort.reservations).isEmpty();
    }

    @Test
    @DisplayName("prepare 는 같은 멱등키로 다른 주문을 요청하면 예외를 던진다")
    void prepare_whenSameIdempotencyKeyUsedForDifferentOrder_thenThrowException() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 5));
        service.prepare(command(orderId, "payment-request-1"));

        OrderId anotherOrderId = OrderId.newId();
        orderPort.saveOrder(Order.create(anotherOrderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 1)
        )));

        assertThatThrownBy(() -> service.prepare(command(anotherOrderId, "payment-request-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    @DisplayName("prepare holds inventory in product id order")
    void prepare_whenOrderHasMultipleProducts_thenHoldInventoryInProductIdOrder() {
        ProductId laterProductId = new ProductId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        ProductId earlierProductId = new ProductId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        orderPort.saveOrder(Order.create(orderId, List.of(
                OrderItem.of(laterProductId, "mouse", Money.won(1000), 1),
                OrderItem.of(earlierProductId, "keyboard", Money.won(2000), 1)
        )));
        inventoryReservationPort.save(Inventory.of(laterProductId, 5));
        inventoryReservationPort.save(Inventory.of(earlierProductId, 5));

        service.prepare(command(orderId, "payment-request-1"));

        assertThat(inventoryReservationPort.heldProductIds)
                .containsExactly(earlierProductId, laterProductId);
    }

    @Test
    @DisplayName("prepare 는 재고가 부족하면 예외를 던진다")
    void prepare_whenInventoryInsufficient_thenThrowException() {
        orderPort.saveOrder(order());
        inventoryReservationPort.save(Inventory.of(productId, 1));

        assertThatThrownBy(() -> service.prepare(command(orderId, "payment-request-1")))
                .isInstanceOf(DomainException.class);
    }

    private Order order() {
        return Order.create(orderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 2)
        ));
    }

    private static PreparePaymentCommand command(OrderId orderId, String idempotencyKey) {
        return new PreparePaymentCommand(orderId, new IdempotencyKey(idempotencyKey));
    }

    private static IdempotencyRecord completedRecord(String key, OrderId orderId, PaymentId paymentId) {
        IdempotencyRecord record = IdempotencyRecord.inFlight(
                key,
                new PreparePaymentRequestHashService().hash(command(orderId, key)),
                LocalDateTime.of(2026, 5, 3, 21, 0),
                LocalDateTime.of(2026, 5, 4, 21, 0)
        );
        record.complete("""
                {"paymentId":"%s","orderId":"%s","amount":2000.00,"orderStatus":"PAYMENT_PENDING","paymentStatus":"READY"}
                """.formatted(paymentId.value(), orderId.value()).trim());
        return record;
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
        private final List<ProductId> heldProductIds = new CopyOnWriteArrayList<>();

        @Override
        public InventoryReservation hold(OrderId orderId, ProductId productId, int quantity, LocalDateTime expiresAt) {
            Inventory inventory = inventories.get(productId);
            if (inventory == null) {
                throw new DomainException("inventory not found");
            }
            inventory.hold(quantity);
            heldProductIds.add(productId);

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
        public Payment getPaymentForUpdate(PaymentId paymentId) {
            return getPayment(paymentId);
        }

        @Override
        public Optional<Payment> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
            return Optional.ofNullable(payments.get(idempotencyKey));
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
            payments.put(payment.idempotencyKey(), payment);
            paymentsById.put(payment.id(), payment);
            saveCount.incrementAndGet();
        }

        int saveCount() {
            return saveCount.get();
        }
    }

    private static class FakeIdempotencyRecordPort
            implements IdempotencyRecordQueryPort, IdempotencyRecordCommandPort {

        private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();
        private IdempotencyRecord existingRecordOnNextSave;

        @Override
        public Optional<IdempotencyRecord> findByKey(String key) {
            return Optional.ofNullable(records.get(key));
        }

        @Override
        public void save(IdempotencyRecord record) {
            if (existingRecordOnNextSave != null) {
                IdempotencyRecord existingRecord = existingRecordOnNextSave;
                existingRecordOnNextSave = null;
                records.put(existingRecord.key(), existingRecord);
                throw new IdempotencyRecordAlreadyExistsException("idempotency record already exists", null);
            }
            records.put(record.key(), record);
        }

        void failNextSaveWithExistingRecord(IdempotencyRecord record) {
            existingRecordOnNextSave = record;
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
