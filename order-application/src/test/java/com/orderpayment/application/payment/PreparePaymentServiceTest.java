package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.orderpayment.application.payment.service.PreparePaymentIdempotencyHandler;
import com.orderpayment.application.payment.service.PreparePaymentIdempotencyResponseSerializer;
import com.orderpayment.application.payment.service.PreparePaymentRequestHashService;
import com.orderpayment.application.payment.service.PreparePaymentService;
import com.orderpayment.application.payment.service.PreparePaymentTransactionService;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.common.event.DomainEvent;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreparePaymentServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 3, 21, 0);
    private final ProductId productId = ProductId.newId();
    private final OrderId orderId = OrderId.newId();
    private final PaymentId paymentId = new PaymentId(UUID.fromString("00000000-0000-0000-0000-000000000201"));
    private final Map<OrderId, Order> orders = new LinkedHashMap<>();
    private final Map<ProductId, Inventory> inventories = new LinkedHashMap<>();
    private final Map<String, IdempotencyRecord> idempotencyRecords = new LinkedHashMap<>();
    private final List<InventoryReservation> reservations = new ArrayList<>();
    private final List<ProductId> heldProductIds = new ArrayList<>();
    private final List<DomainEvent> publishedEvents = new ArrayList<>();

    @Mock
    private OrderQueryPort orderQueryPort;

    @Mock
    private OrderCommandPort orderCommandPort;

    @Mock
    private InventoryReservationCommandPort inventoryReservationCommandPort;

    @Mock
    private PaymentCommandPort paymentCommandPort;

    @Mock
    private IdempotencyRecordQueryPort idempotencyRecordQueryPort;

    @Mock
    private IdempotencyRecordCommandPort idempotencyRecordCommandPort;

    @Mock
    private DomainEventPublisherPort eventPublisher;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private PreparePaymentRequestHashService requestHashService;
    private PreparePaymentIdempotencyResponseSerializer responseSerializer;
    private PreparePaymentService service;

    @BeforeEach
    void setUp() {
        requestHashService = new PreparePaymentRequestHashService();
        responseSerializer = new PreparePaymentIdempotencyResponseSerializer(new ObjectMapper());
        PreparePaymentIdempotencyHandler idempotencyHandler = new PreparePaymentIdempotencyHandler(
                idempotencyRecordQueryPort,
                idempotencyRecordCommandPort,
                () -> now,
                requestHashService,
                responseSerializer
        );
        PreparePaymentTransactionService transactionService = new PreparePaymentTransactionService(
                orderQueryPort,
                orderCommandPort,
                inventoryReservationCommandPort,
                paymentCommandPort,
                () -> paymentId,
                () -> now,
                eventPublisher,
                idempotencyHandler
        );
        service = new PreparePaymentService(transactionService);

        stubStatefulPorts();
    }

    @Test
    @DisplayName("prepare 는 결제를 준비하고 재고를 보류한다")
    void prepare_whenOrderExistsAndInventoryAvailable_thenPreparePaymentAndHoldInventory() {
        saveOrder(order());
        saveInventory(Inventory.of(productId, 5));

        PreparePaymentResult result = service.prepare(command(orderId, "payment-request-1"));

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.amount()).isEqualTo(Money.won(2000));
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(inventories.get(productId).availableQuantity()).isEqualTo(3);
        assertThat(inventories.get(productId).heldQuantity()).isEqualTo(2);
        assertThat(reservations).hasSize(1);
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("prepare 는 같은 멱등키 요청이면 완료된 결과를 재현한다")
    void prepare_whenSameIdempotencyKeyRequestedAgain_thenReturnExistingPaymentResult() {
        saveOrder(order());
        saveInventory(Inventory.of(productId, 5));

        PreparePaymentResult firstResult = service.prepare(command(orderId, "payment-request-1"));
        PreparePaymentResult secondResult = service.prepare(command(orderId, "payment-request-1"));

        assertThat(secondResult).isEqualTo(firstResult);
        verify(paymentCommandPort, times(1)).savePayment(any(Payment.class));
        assertThat(reservations).hasSize(1);
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("prepare 는 멱등성 레코드 생성 충돌 시 기존 결과를 재현한다")
    void prepare_whenIdempotencyRecordAlreadyExists_thenReplayExistingResult() {
        saveOrder(order());
        saveInventory(Inventory.of(productId, 5));
        IdempotencyRecord existingRecord = completedRecord("payment-request-1", orderId, paymentId);
        doAnswer(invocation -> {
            idempotencyRecords.put(existingRecord.key(), existingRecord);
            throw new IdempotencyRecordAlreadyExistsException("idempotency record already exists", null);
        }).when(idempotencyRecordCommandPort).save(any(IdempotencyRecord.class));

        PreparePaymentResult result = service.prepare(command(orderId, "payment-request-1"));

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.amount()).isEqualTo(Money.won(2000));
        verify(paymentCommandPort, times(0)).savePayment(any(Payment.class));
        assertThat(reservations).isEmpty();
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    @DisplayName("prepare 는 같은 멱등키 요청이 처리 중이면 예외를 던진다")
    void prepare_whenSameIdempotencyKeyIsInFlight_thenThrowException() {
        saveOrder(order());
        saveInventory(Inventory.of(productId, 5));
        idempotencyRecords.put("payment-request-1", IdempotencyRecord.inFlight(
                "payment-request-1",
                requestHashService.hash(command(orderId, "payment-request-1")),
                now,
                now.plusDays(1)
        ));

        assertThatThrownBy(() -> service.prepare(command(orderId, "payment-request-1")))
                .isInstanceOf(IdempotencyInFlightException.class);
        verify(paymentCommandPort, times(0)).savePayment(any(Payment.class));
        assertThat(reservations).isEmpty();
    }

    @Test
    @DisplayName("prepare 는 같은 멱등키로 다른 주문을 요청하면 충돌 예외를 던진다")
    void prepare_whenSameIdempotencyKeyUsedForDifferentOrder_thenThrowException() {
        saveOrder(order());
        saveInventory(Inventory.of(productId, 5));
        service.prepare(command(orderId, "payment-request-1"));

        OrderId anotherOrderId = OrderId.newId();
        saveOrder(Order.create(anotherOrderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 1)
        )));

        assertThatThrownBy(() -> service.prepare(command(anotherOrderId, "payment-request-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    @DisplayName("prepare 는 상품 ID 순서로 재고를 보류한다")
    void prepare_whenOrderHasMultipleProducts_thenHoldInventoryInProductIdOrder() {
        ProductId laterProductId = new ProductId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        ProductId earlierProductId = new ProductId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        saveOrder(Order.create(orderId, List.of(
                OrderItem.of(laterProductId, "mouse", Money.won(1000), 1),
                OrderItem.of(earlierProductId, "keyboard", Money.won(2000), 1)
        )));
        saveInventory(Inventory.of(laterProductId, 5));
        saveInventory(Inventory.of(earlierProductId, 5));

        service.prepare(command(orderId, "payment-request-1"));

        assertThat(heldProductIds).containsExactly(earlierProductId, laterProductId);
    }

    @Test
    @DisplayName("prepare 는 재고가 부족하면 예외를 던진다")
    void prepare_whenInventoryInsufficient_thenThrowException() {
        saveOrder(order());
        saveInventory(Inventory.of(productId, 1));

        assertThatThrownBy(() -> service.prepare(command(orderId, "payment-request-1")))
                .isInstanceOf(DomainException.class);
    }

    private void stubStatefulPorts() {
        when(idempotencyRecordQueryPort.findByKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(idempotencyRecords.get(invocation.getArgument(0))));
        doAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            idempotencyRecords.put(record.key(), record);
            return null;
        }).when(idempotencyRecordCommandPort).save(any(IdempotencyRecord.class));
        when(orderQueryPort.getOrder(any(OrderId.class)))
                .thenAnswer(invocation -> Optional.ofNullable(orders.get(invocation.getArgument(0)))
                        .orElseThrow(() -> new DomainException("order not found")));
        doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            orders.put(order.id(), order);
            return null;
        }).when(orderCommandPort).saveOrder(any(Order.class));
        when(inventoryReservationCommandPort.hold(
                any(OrderId.class),
                any(ProductId.class),
                anyInt(),
                any(LocalDateTime.class)
        )).thenAnswer(invocation -> {
            OrderId orderId = invocation.getArgument(0);
            ProductId productId = invocation.getArgument(1);
            int quantity = invocation.getArgument(2);
            LocalDateTime expiresAt = invocation.getArgument(3);
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
            reservations.add(reservation);
            return reservation;
        });
        doAnswer(invocation -> {
            List<DomainEvent> events = invocation.getArgument(0);
            publishedEvents.addAll(events);
            return null;
        }).when(eventPublisher).publishAll(any());
    }

    private void saveOrder(Order order) {
        orders.put(order.id(), order);
    }

    private void saveInventory(Inventory inventory) {
        inventories.put(inventory.productId(), inventory);
    }

    private Order order() {
        return Order.create(orderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 2)
        ));
    }

    private static PreparePaymentCommand command(OrderId orderId, String idempotencyKey) {
        return new PreparePaymentCommand(orderId, new IdempotencyKey(idempotencyKey));
    }

    private IdempotencyRecord completedRecord(String key, OrderId orderId, PaymentId paymentId) {
        IdempotencyRecord record = IdempotencyRecord.inFlight(
                key,
                requestHashService.hash(command(orderId, key)),
                now,
                now.plusDays(1)
        );
        record.complete("""
                {"paymentId":"%s","orderId":"%s","amount":2000.00,"orderStatus":"PAYMENT_PENDING","paymentStatus":"READY"}
                """.formatted(paymentId.value(), orderId.value()).trim());
        return record;
    }
}
