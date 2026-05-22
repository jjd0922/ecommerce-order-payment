package com.orderpayment.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.InventoryReservationQueryPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import com.orderpayment.application.payment.service.ConfirmPaymentIdempotencyHandler;
import com.orderpayment.application.payment.service.ConfirmPaymentIdempotencyResponseSerializer;
import com.orderpayment.application.payment.service.ConfirmPaymentRequestHashService;
import com.orderpayment.application.payment.service.ConfirmPaymentService;
import com.orderpayment.application.payment.service.ConfirmPaymentTransactionService;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
import com.orderpayment.domain.inventory.InventoryReservationStatus;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.Payment;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;
import com.orderpayment.infrastructure.config.InfrastructureJpaConfiguration;
import com.orderpayment.infrastructure.inventory.InventoryJpaEntity;
import com.orderpayment.infrastructure.inventory.InventoryJpaRepository;
import com.orderpayment.infrastructure.inventory.InventoryReservationJpaEntity;
import com.orderpayment.infrastructure.inventory.InventoryReservationJpaRepository;
import com.orderpayment.infrastructure.inventory.InventoryReservationPersistenceAdapter;
import com.orderpayment.infrastructure.order.OrderItemJpaEntity;
import com.orderpayment.infrastructure.order.OrderJpaEntity;
import com.orderpayment.infrastructure.order.OrderJpaRepository;
import com.orderpayment.infrastructure.order.OrderPersistenceAdapter;
import com.orderpayment.infrastructure.support.MysqlContainerTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(InfrastructureJpaConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentPessimisticLockIntegrationTest extends MysqlContainerTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 20, 10, 0);

    private final OrderId orderId = OrderId.newId();
    private final PaymentId paymentId = PaymentId.newId();
    private final String productId = UUID.randomUUID().toString();
    private final IdempotencyKey idempotencyKey = new IdempotencyKey("confirm-lock-key");

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private InventoryReservationJpaRepository inventoryReservationJpaRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerDataSourceProperties(registry);
    }

    @BeforeEach
    void setUp() {
        insertPreparedPaymentState();
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection connection = getConnection()) {
            deleteTestData(connection);
        }
    }

    @Test
    @DisplayName("same payment confirm requests use pessimistic lock and approve once")
    void confirm_whenSamePaymentRequestedConcurrently_thenApproveOnlyOnce() throws Exception {
        BlockingPaymentApprovalPort approvalPort = new BlockingPaymentApprovalPort();
        PaymentPersistenceAdapter paymentAdapter = new PaymentPersistenceAdapter(paymentJpaRepository);
        OrderPersistenceAdapter orderAdapter = new OrderPersistenceAdapter(orderJpaRepository);
        InventoryReservationPersistenceAdapter reservationAdapter =
                new InventoryReservationPersistenceAdapter(inventoryJpaRepository, inventoryReservationJpaRepository);
        FakeIdempotencyRecordPort idempotencyRecordPort = new FakeIdempotencyRecordPort();
        ConfirmPaymentTransactionService transactionService = new ConfirmPaymentTransactionService(
                paymentAdapter,
                paymentAdapter,
                orderAdapter,
                orderAdapter,
                reservationAdapter,
                reservationAdapter,
                fixedTimePort(),
                events -> {
                },
                new ConfirmPaymentIdempotencyHandler(
                        idempotencyRecordPort,
                        idempotencyRecordPort,
                        fixedTimePort(),
                        new ConfirmPaymentRequestHashService(),
                        new ConfirmPaymentIdempotencyResponseSerializer(new ObjectMapper())
                )
        );
        ConfirmPaymentService service = new ConfirmPaymentService(
                approvalPort,
                transactionService,
                new SimpleMeterRegistry()
        );
        int requestCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                await(startLatch);
                service.confirm(new ConfirmPaymentCommand(paymentId, idempotencyKey));
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(approvalPort.awaitApprovalStarted()).isTrue();
        approvalPort.releaseApproval();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        Payment payment = paymentAdapter.getPayment(paymentId);
        assertThat(approvalPort.approveCount()).isEqualTo(1);
        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(orderAdapter.getOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(inventoryReservationJpaRepository.findByOrderIdAndStatus(
                orderId.value().toString(),
                InventoryReservationStatus.HELD
        )).isEmpty();
    }

    private void insertPreparedPaymentState() {
        insertProduct();
        OrderJpaEntity order = new OrderJpaEntity(
                orderId.value().toString(),
                BigDecimal.valueOf(2000),
                OrderStatus.PAYMENT_PENDING
        );
        order.addItem(new OrderItemJpaEntity(
                productId,
                "keyboard",
                BigDecimal.valueOf(1000),
                2
        ));
        orderJpaRepository.save(order);
        paymentJpaRepository.save(new PaymentJpaEntity(
                paymentId.value().toString(),
                orderId.value().toString(),
                BigDecimal.valueOf(2000),
                idempotencyKey.value(),
                PaymentStatus.READY,
                null,
                null
        ));
        inventoryJpaRepository.save(new InventoryJpaEntity(productId, 3, 2));
        inventoryReservationJpaRepository.save(new InventoryReservationJpaEntity(
                UUID.randomUUID().toString(),
                orderId.value().toString(),
                productId,
                2,
                NOW.plusMinutes(10),
                InventoryReservationStatus.HELD
        ));
    }

    private void insertProduct() {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO product (id, name, price, selling)
                     VALUES (?, ?, ?, ?)
                     """)) {
            statement.setString(1, productId);
            statement.setString(2, "keyboard");
            statement.setBigDecimal(3, BigDecimal.valueOf(1000));
            statement.setBoolean(4, true);
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void deleteTestData(Connection connection) throws Exception {
        try (PreparedStatement deleteReservation = connection.prepareStatement("DELETE FROM inventory_reservation WHERE order_id = ?");
             PreparedStatement deletePayment = connection.prepareStatement("DELETE FROM payment WHERE order_id = ?");
             PreparedStatement deleteOrderItem = connection.prepareStatement("DELETE FROM order_item WHERE order_id = ?");
             PreparedStatement deleteOrder = connection.prepareStatement("DELETE FROM orders WHERE id = ?");
             PreparedStatement deleteInventory = connection.prepareStatement("DELETE FROM inventory WHERE product_id = ?");
             PreparedStatement deleteProduct = connection.prepareStatement("DELETE FROM product WHERE id = ?")) {
            deleteReservation.setString(1, orderId.value().toString());
            deleteReservation.executeUpdate();
            deletePayment.setString(1, orderId.value().toString());
            deletePayment.executeUpdate();
            deleteOrderItem.setString(1, orderId.value().toString());
            deleteOrderItem.executeUpdate();
            deleteOrder.setString(1, orderId.value().toString());
            deleteOrder.executeUpdate();
            deleteInventory.setString(1, productId);
            deleteInventory.executeUpdate();
            deleteProduct.setString(1, productId);
            deleteProduct.executeUpdate();
        }
    }

    private static CurrentTimePort fixedTimePort() {
        return () -> NOW;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static class BlockingPaymentApprovalPort implements PaymentApprovalPort {

        private final AtomicInteger approveCount = new AtomicInteger();
        private final CountDownLatch approvalStartedLatch = new CountDownLatch(1);
        private final CountDownLatch releaseApprovalLatch = new CountDownLatch(1);

        @Override
        public PaymentApprovalResult approve(Payment payment) {
            approveCount.incrementAndGet();
            approvalStartedLatch.countDown();
            await(releaseApprovalLatch);
            return PaymentApprovalResult.approved("pg-" + payment.id().value());
        }

        boolean awaitApprovalStarted() throws InterruptedException {
            return approvalStartedLatch.await(5, TimeUnit.SECONDS);
        }

        void releaseApproval() {
            releaseApprovalLatch.countDown();
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
}
