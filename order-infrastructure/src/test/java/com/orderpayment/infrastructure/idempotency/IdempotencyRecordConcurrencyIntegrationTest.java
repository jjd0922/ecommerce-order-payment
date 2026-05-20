package com.orderpayment.infrastructure.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderIdGeneratorPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.order.port.out.ProductQueryPort;
import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentIdGeneratorPort;
import com.orderpayment.application.payment.service.PreparePaymentIdempotencyHandler;
import com.orderpayment.application.payment.service.PreparePaymentService;
import com.orderpayment.application.payment.service.PreparePaymentRequestHashService;
import com.orderpayment.application.payment.service.PreparePaymentIdempotencyResponseSerializer;
import com.orderpayment.application.payment.service.PreparePaymentTransactionService;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.inventory.InventoryReservationStatus;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;
import com.orderpayment.infrastructure.config.InfrastructureJpaConfiguration;
import com.orderpayment.infrastructure.inventory.InventoryJpaEntity;
import com.orderpayment.infrastructure.inventory.InventoryJpaRepository;
import com.orderpayment.infrastructure.inventory.InventoryReservationJpaRepository;
import com.orderpayment.infrastructure.inventory.InventoryReservationPersistenceAdapter;
import com.orderpayment.infrastructure.order.OrderItemJpaEntity;
import com.orderpayment.infrastructure.order.OrderJpaEntity;
import com.orderpayment.infrastructure.order.OrderJpaRepository;
import com.orderpayment.infrastructure.order.OrderPersistenceAdapter;
import com.orderpayment.infrastructure.payment.PaymentJpaRepository;
import com.orderpayment.infrastructure.payment.PaymentPersistenceAdapter;
import com.orderpayment.infrastructure.support.MysqlContainerTestSupport;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({
        InfrastructureJpaConfiguration.class,
        IdempotencyRecordConcurrencyIntegrationTest.TestConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdempotencyRecordConcurrencyIntegrationTest extends MysqlContainerTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 20, 10, 0);
    private static final PaymentId PAYMENT_ID = new PaymentId(UUID.fromString("00000000-0000-0000-0000-000000002201"));

    private final OrderId orderId = OrderId.newId();
    private final String productId = UUID.randomUUID().toString();
    private final IdempotencyKey idempotencyKey = new IdempotencyKey("prepare-concurrent-key");

    @Autowired
    private PreparePaymentService preparePaymentService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private InventoryReservationJpaRepository inventoryReservationJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private IdempotencyRecordJpaRepository idempotencyRecordJpaRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerDataSourceProperties(registry);
    }

    @BeforeEach
    void setUp() {
        insertProduct();
        insertOrder();
        inventoryJpaRepository.save(new InventoryJpaEntity(productId, 5, 0));
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection connection = getConnection()) {
            deleteTestData(connection);
        }
    }

    @Test
    @DisplayName("same idempotency key concurrent prepare creates one payment and one record")
    void prepare_whenSameIdempotencyKeyArrivesConcurrently_thenCreateOnePaymentAndOneRecord() throws Exception {
        int requestCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        Queue<PreparePaymentResult> results = new ConcurrentLinkedQueue<>();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                await(startLatch);
                try {
                    results.add(preparePaymentService.prepare(new PreparePaymentCommand(orderId, idempotencyKey)));
                } catch (Throwable throwable) {
                    failures.add(throwable);
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).isEmpty();
        assertThat(results).hasSize(2);
        assertThat(results.stream().map(PreparePaymentResult::paymentId).distinct())
                .containsExactly(PAYMENT_ID);
        assertThat(paymentJpaRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordJpaRepository.count()).isEqualTo(1);
        assertThat(inventoryReservationJpaRepository.findByOrderIdAndStatus(
                orderId.value().toString(),
                InventoryReservationStatus.HELD
        )).hasSize(1);
        assertThat(inventoryJpaRepository.findById(productId).orElseThrow().heldQuantity()).isEqualTo(2);
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

    private void insertOrder() {
        OrderJpaEntity order = new OrderJpaEntity(
                orderId.value().toString(),
                BigDecimal.valueOf(2000),
                OrderStatus.CREATED
        );
        order.addItem(new OrderItemJpaEntity(
                productId,
                "keyboard",
                BigDecimal.valueOf(1000),
                2
        ));
        orderJpaRepository.save(order);
    }

    private void deleteTestData(Connection connection) throws Exception {
        try (PreparedStatement deleteReservation = connection.prepareStatement("DELETE FROM inventory_reservation WHERE order_id = ?");
             PreparedStatement deletePayment = connection.prepareStatement("DELETE FROM payment WHERE order_id = ?");
             PreparedStatement deleteOrderItem = connection.prepareStatement("DELETE FROM order_item WHERE order_id = ?");
             PreparedStatement deleteOrder = connection.prepareStatement("DELETE FROM orders WHERE id = ?");
             PreparedStatement deleteInventory = connection.prepareStatement("DELETE FROM inventory WHERE product_id = ?");
             PreparedStatement deleteProduct = connection.prepareStatement("DELETE FROM product WHERE id = ?");
             PreparedStatement deleteIdempotency = connection.prepareStatement("DELETE FROM idempotency_record WHERE idempotency_key = ?")) {
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
            deleteIdempotency.setString(1, idempotencyKey.value());
            deleteIdempotency.executeUpdate();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        OrderPersistenceAdapter orderPersistenceAdapter(OrderJpaRepository orderJpaRepository) {
            return new OrderPersistenceAdapter(orderJpaRepository);
        }

        @Bean
        PaymentPersistenceAdapter paymentPersistenceAdapter(PaymentJpaRepository paymentJpaRepository) {
            return new PaymentPersistenceAdapter(paymentJpaRepository);
        }

        @Bean
        InventoryReservationPersistenceAdapter inventoryReservationPersistenceAdapter(
                InventoryJpaRepository inventoryJpaRepository,
                InventoryReservationJpaRepository inventoryReservationJpaRepository
        ) {
            return new InventoryReservationPersistenceAdapter(inventoryJpaRepository, inventoryReservationJpaRepository);
        }

        @Bean
        IdempotencyRecordPersistenceAdapter idempotencyRecordPersistenceAdapter(
                IdempotencyRecordJpaRepository idempotencyRecordJpaRepository
        ) {
            return new IdempotencyRecordPersistenceAdapter(idempotencyRecordJpaRepository);
        }

        @Bean
        PreparePaymentTransactionService preparePaymentTransactionService(
                OrderPersistenceAdapter orderPersistenceAdapter,
                InventoryReservationPersistenceAdapter inventoryReservationPersistenceAdapter,
                PaymentPersistenceAdapter paymentPersistenceAdapter,
                PreparePaymentIdempotencyHandler idempotencyHandler
        ) {
            return new PreparePaymentTransactionService(
                    orderPersistenceAdapter,
                    orderPersistenceAdapter,
                    inventoryReservationPersistenceAdapter,
                    paymentPersistenceAdapter,
                    () -> PAYMENT_ID,
                    () -> NOW,
                    events -> {
                    },
                    idempotencyHandler
            );
        }

        @Bean
        PreparePaymentIdempotencyHandler preparePaymentIdempotencyHandler(
                IdempotencyRecordPersistenceAdapter idempotencyRecordPersistenceAdapter
        ) {
            return new PreparePaymentIdempotencyHandler(
                    idempotencyRecordPersistenceAdapter,
                    idempotencyRecordPersistenceAdapter,
                    () -> NOW,
                    new PreparePaymentRequestHashService(),
                    new PreparePaymentIdempotencyResponseSerializer(new ObjectMapper())
            );
        }

        @Bean
        PreparePaymentService preparePaymentService(PreparePaymentTransactionService transactionService) {
            return new PreparePaymentService(transactionService);
        }
    }
}
