package com.orderpayment.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderpayment.infrastructure.config.InfrastructureJpaConfiguration;
import com.orderpayment.infrastructure.event.LoggingDomainEventPublisherAdapter;
import com.orderpayment.infrastructure.support.MysqlContainerTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
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
        OutboxSkipLockedIntegrationTest.TestConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxSkipLockedIntegrationTest extends MysqlContainerTestSupport {

    private final String eventId = UUID.randomUUID().toString();

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private OutboxEventRelayService outboxEventRelayService;

    @Autowired
    private BlockingOutboxPublisher publisher;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerDataSourceProperties(registry);
    }

    @BeforeEach
    void setUp() {
        outboxEventJpaRepository.save(new OutboxEventJpaEntity(
                eventId,
                "PaymentApproved",
                "payment-1",
                "{\"paymentId\":\"payment-1\"}",
                LocalDateTime.of(2026, 5, 20, 10, 0),
                LocalDateTime.of(2026, 5, 20, 10, 0)
        ));
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM outbox_event WHERE id = ?")) {
            statement.setString(1, eventId);
            statement.executeUpdate();
        }
    }

    @Test
    @DisplayName("outbox relay workers skip locked event claimed by another worker")
    void publishPendingEvents_whenTwoRelaysRunConcurrently_thenPublishEventOnce() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Queue<Integer> publishedCounts = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < 2; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                await(startLatch);
                publishedCounts.add(outboxEventRelayService.publishPendingEvents());
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(publisher.awaitPublishStarted()).isTrue();
        publisher.releasePublish();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(publisher.publishCount()).isEqualTo(1);
        assertThat(publishedCounts).containsExactlyInAnyOrder(0, 1);
        assertThat(outboxEventJpaRepository.findById(eventId).orElseThrow().status())
                .isEqualTo(OutboxEventStatus.PUBLISHED);
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
        BlockingOutboxPublisher blockingOutboxPublisher() {
            return new BlockingOutboxPublisher();
        }

        @Bean
        OutboxEventRelayService outboxEventRelayService(
                OutboxEventJpaRepository outboxEventJpaRepository,
                BlockingOutboxPublisher publisher
        ) {
            return new OutboxEventRelayService(
                    outboxEventJpaRepository,
                    publisher,
                    new SimpleMeterRegistry()
            );
        }
    }

    static class BlockingOutboxPublisher extends LoggingDomainEventPublisherAdapter {

        private final AtomicInteger publishCount = new AtomicInteger();
        private final CountDownLatch publishStartedLatch = new CountDownLatch(1);
        private final CountDownLatch releasePublishLatch = new CountDownLatch(1);

        @Override
        public void publish(OutboxEventJpaEntity event) {
            publishCount.incrementAndGet();
            publishStartedLatch.countDown();
            await(releasePublishLatch);
        }

        boolean awaitPublishStarted() throws InterruptedException {
            return publishStartedLatch.await(5, TimeUnit.SECONDS);
        }

        void releasePublish() {
            releasePublishLatch.countDown();
        }

        int publishCount() {
            return publishCount.get();
        }
    }
}
