package com.orderpayment.infrastructure.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderpayment.infrastructure.support.MysqlContainerTestSupport;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryReservationConcurrencyIntegrationTest extends MysqlContainerTestSupport {

    private static final int INITIAL_STOCK = 5;
    private static final int ATTEMPT_COUNT = 20;

    private final String productId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = getConnection()) {
            deleteTestData(connection);
            insertProduct(connection);
            insertInventory(connection);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection connection = getConnection()) {
            deleteTestData(connection);
        }
    }

    @Test
    @DisplayName("조건부 재고 업데이트는 동시 예약 요청에서 초과 판매를 방지한다")
    void holdInventory_whenConcurrentReservationRequests_thenPreventOverselling() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(ATTEMPT_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(ATTEMPT_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < ATTEMPT_COUNT; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                await(startLatch);
                if (holdInventory(productId, 1) == 1) {
                    successCount.incrementAndGet();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        InventorySnapshot inventory = getInventory(productId);
        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
        assertThat(inventory.availableQuantity()).isZero();
        assertThat(inventory.heldQuantity()).isEqualTo(INITIAL_STOCK);
    }

    private void insertProduct(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO product (id, name, price, selling)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, productId);
            statement.setString(2, "concurrency-test-product");
            statement.setBigDecimal(3, BigDecimal.valueOf(1000));
            statement.setBoolean(4, true);
            statement.executeUpdate();
        }
    }

    private void insertInventory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO inventory (product_id, available_quantity, held_quantity)
                VALUES (?, ?, ?)
                """)) {
            statement.setString(1, productId);
            statement.setInt(2, INITIAL_STOCK);
            statement.setInt(3, 0);
            statement.executeUpdate();
        }
    }

    private void deleteTestData(Connection connection) throws SQLException {
        try (PreparedStatement deleteInventory = connection.prepareStatement("""
                DELETE FROM inventory
                WHERE product_id = ?
                """);
             PreparedStatement deleteProduct = connection.prepareStatement("""
                     DELETE FROM product
                     WHERE id = ?
                     """)) {
            deleteInventory.setString(1, productId);
            deleteInventory.executeUpdate();
            deleteProduct.setString(1, productId);
            deleteProduct.executeUpdate();
        }
    }

    private static int holdInventory(String productId, int quantity) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE inventory
                     SET available_quantity = available_quantity - ?,
                         held_quantity = held_quantity + ?
                     WHERE product_id = ?
                       AND available_quantity >= ?
                     """)) {
            statement.setInt(1, quantity);
            statement.setInt(2, quantity);
            statement.setString(3, productId);
            statement.setInt(4, quantity);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static InventorySnapshot getInventory(String productId) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT available_quantity, held_quantity
                     FROM inventory
                     WHERE product_id = ?
                     """)) {
            statement.setString(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("inventory not found");
                }
                return new InventorySnapshot(resultSet.getInt("available_quantity"), resultSet.getInt("held_quantity"));
            }
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

    private record InventorySnapshot(int availableQuantity, int heldQuantity) {
    }
}
