package com.orderpayment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecoverProcessingPaymentsServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 20, 10, 0);
    private final ProductId productId = ProductId.newId();
    private final OrderId orderId = OrderId.newId();
    private final PaymentId paymentId = new PaymentId(UUID.fromString("00000000-0000-0000-0000-000000000901"));
    private final IdempotencyKey idempotencyKey = new IdempotencyKey("payment-request-1");
    private final Map<OrderId, Order> orders = new LinkedHashMap<>();
    private final Map<PaymentId, Payment> payments = new LinkedHashMap<>();
    private final Map<ProductId, Inventory> inventories = new LinkedHashMap<>();
    private final Map<PaymentId, PaymentApprovalResult> approvalResults = new LinkedHashMap<>();
    private final Map<String, IdempotencyRecord> idempotencyRecords = new LinkedHashMap<>();
    private final List<InventoryReservation> reservations = new ArrayList<>();
    private final List<DomainEvent> publishedEvents = new ArrayList<>();

    @Mock
    private OrderQueryPort orderQueryPort;

    @Mock
    private OrderCommandPort orderCommandPort;

    @Mock
    private PaymentQueryPort paymentQueryPort;

    @Mock
    private PaymentCommandPort paymentCommandPort;

    @Mock
    private InventoryReservationQueryPort inventoryReservationQueryPort;

    @Mock
    private InventoryReservationCommandPort inventoryReservationCommandPort;

    @Mock
    private PaymentApprovalQueryPort paymentApprovalQueryPort;

    @Mock
    private DomainEventPublisherPort eventPublisher;

    @Mock
    private IdempotencyRecordQueryPort idempotencyRecordQueryPort;

    @Mock
    private IdempotencyRecordCommandPort idempotencyRecordCommandPort;

    private RecoverProcessingPaymentsService service;

    @BeforeEach
    void setUp() {
        ConfirmPaymentIdempotencyHandler idempotencyHandler = new ConfirmPaymentIdempotencyHandler(
                idempotencyRecordQueryPort,
                idempotencyRecordCommandPort,
                () -> now,
                new ConfirmPaymentRequestHashService(),
                new ConfirmPaymentIdempotencyResponseSerializer(new ObjectMapper())
        );
        ConfirmPaymentTransactionService transactionService = new ConfirmPaymentTransactionService(
                paymentQueryPort,
                paymentCommandPort,
                orderQueryPort,
                orderCommandPort,
                inventoryReservationQueryPort,
                inventoryReservationCommandPort,
                () -> now,
                eventPublisher,
                idempotencyHandler
        );
        service = new RecoverProcessingPaymentsService(
                paymentQueryPort,
                paymentApprovalQueryPort,
                transactionService,
                () -> now,
                Duration.ofMinutes(5),
                100
        );
        stubStatefulPorts();
    }

    @Test
    @DisplayName("recover 는 타임아웃된 처리 중 결제가 PG 승인 상태이면 승인 처리한다")
    void recover_whenProcessingPaymentApprovedByPg_thenApprovePaymentAndConfirmReservation() {
        givenProcessingPaymentTimedOut();
        approvalResults.put(paymentId, PaymentApprovalResult.approved("pg-transaction-1"));

        RecoverProcessingPaymentsResult result = service.recover();

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(payments.get(paymentId).status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payments.get(paymentId).pgTransactionId()).isEqualTo("pg-transaction-1");
        assertThat(orders.get(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(firstReservation().status()).isEqualTo(InventoryReservationStatus.CONFIRMED);
        assertThat(inventories.get(productId).heldQuantity()).isZero();
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("recover 는 타임아웃된 처리 중 결제가 PG 실패 상태이면 실패 처리한다")
    void recover_whenProcessingPaymentFailedByPg_thenFailPaymentAndReleaseReservation() {
        givenProcessingPaymentTimedOut();
        approvalResults.put(paymentId, PaymentApprovalResult.failed("pg-transaction-2", "mock approval failed"));

        RecoverProcessingPaymentsResult result = service.recover();

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(payments.get(paymentId).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payments.get(paymentId).pgTransactionId()).isEqualTo("pg-transaction-2");
        assertThat(orders.get(orderId).status()).isEqualTo(OrderStatus.FAILED);
        assertThat(firstReservation().status()).isEqualTo(InventoryReservationStatus.RELEASED);
        assertThat(inventories.get(productId).availableQuantity()).isEqualTo(5);
        assertThat(publishedEvents).hasSize(2);
    }

    @Test
    @DisplayName("recover 는 PG 결과를 알 수 없으면 처리 중 결제를 유지한다")
    void recover_whenPgResultUnknown_thenLeavePaymentProcessing() {
        givenProcessingPaymentTimedOut();

        RecoverProcessingPaymentsResult result = service.recover();

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isZero();
        assertThat(payments.get(paymentId).status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(orders.get(orderId).status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(firstReservation().status()).isEqualTo(InventoryReservationStatus.HELD);
        assertThat(publishedEvents).isEmpty();
    }

    private void stubStatefulPorts() {
        when(idempotencyRecordQueryPort.findByKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(idempotencyRecords.get(invocation.getArgument(0))));
        doAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            idempotencyRecords.put(record.key(), record);
            return null;
        }).when(idempotencyRecordCommandPort).save(any(IdempotencyRecord.class));
        when(paymentQueryPort.findProcessingPaymentsRequestedBefore(any(LocalDateTime.class), anyInt()))
                .thenAnswer(invocation -> {
                    LocalDateTime requestedBefore = invocation.getArgument(0);
                    int limit = invocation.getArgument(1);
                    return payments.values().stream()
                            .filter(payment -> payment.status() == PaymentStatus.PROCESSING)
                            .filter(payment -> payment.approvalRequestedAt() != null)
                            .filter(payment -> payment.approvalRequestedAt().isBefore(requestedBefore))
                            .limit(limit)
                            .toList();
                });
        when(paymentApprovalQueryPort.findApprovalResult(any(Payment.class)))
                .thenAnswer(invocation -> Optional.ofNullable(approvalResults.get(invocation.<Payment>getArgument(0).id())));
        when(paymentQueryPort.getPaymentForUpdate(any(PaymentId.class)))
                .thenAnswer(invocation -> payment(invocation.getArgument(0)));
        when(orderQueryPort.getOrder(any(OrderId.class)))
                .thenAnswer(invocation -> order(invocation.getArgument(0)));
        doAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payments.put(payment.id(), payment);
            return null;
        }).when(paymentCommandPort).savePayment(any(Payment.class));
        doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            orders.put(order.id(), order);
            return null;
        }).when(orderCommandPort).saveOrder(any(Order.class));
        when(inventoryReservationQueryPort.findHeldReservationsByOrderId(any(OrderId.class))).thenAnswer(invocation -> {
            OrderId orderId = invocation.getArgument(0);
            return reservations.stream()
                    .filter(reservation -> reservation.orderId().equals(orderId))
                    .filter(reservation -> reservation.status() == InventoryReservationStatus.HELD)
                    .toList();
        });
        doAnswer(invocation -> {
            invocation.<List<InventoryReservation>>getArgument(0)
                    .forEach(reservation -> inventories.get(reservation.productId()).confirm(reservation.quantity()));
            return null;
        }).when(inventoryReservationCommandPort).confirmAll(any());
        doAnswer(invocation -> {
            invocation.<List<InventoryReservation>>getArgument(0)
                    .forEach(reservation -> inventories.get(reservation.productId()).release(reservation.quantity()));
            return null;
        }).when(inventoryReservationCommandPort).releaseAll(any());
        doAnswer(invocation -> {
            publishedEvents.addAll(invocation.getArgument(0));
            return null;
        }).when(eventPublisher).publishAll(any());
    }

    private void givenProcessingPaymentTimedOut() {
        Order order = Order.create(orderId, List.of(
                OrderItem.of(productId, "keyboard", Money.won(1000), 2)
        ));
        order.requestPayment();
        orders.put(orderId, order);

        Payment payment = Payment.ready(paymentId, orderId, Money.won(2000), idempotencyKey);
        payment.startApproval(now.minusMinutes(10));
        payments.put(paymentId, payment);

        reservations.add(InventoryReservation.hold(
                InventoryReservationId.newId(),
                orderId,
                productId,
                2,
                now.plusMinutes(10)
        ));
        inventories.put(productId, Inventory.restore(productId, 3, 2));
    }

    private Payment payment(PaymentId paymentId) {
        return Optional.ofNullable(payments.get(paymentId))
                .orElseThrow(() -> new DomainException("payment not found"));
    }

    private Order order(OrderId orderId) {
        return Optional.ofNullable(orders.get(orderId))
                .orElseThrow(() -> new DomainException("order not found"));
    }

    private InventoryReservation firstReservation() {
        return reservations.get(0);
    }
}
