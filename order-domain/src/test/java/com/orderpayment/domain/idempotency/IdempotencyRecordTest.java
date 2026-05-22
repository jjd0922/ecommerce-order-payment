package com.orderpayment.domain.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.domain.common.DomainException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 5, 20, 10, 0);

    @Test
    @DisplayName("inFlight creates an IN_FLIGHT idempotency record")
    void inFlight_whenCalled_thenCreateInFlightRecord() {
        IdempotencyRecord record = IdempotencyRecord.inFlight("key-1", "hash-1", now, now.plusDays(1));

        assertThat(record.key()).isEqualTo("key-1");
        assertThat(record.requestHash()).isEqualTo("hash-1");
        assertThat(record.status()).isEqualTo(IdempotencyRecordStatus.IN_FLIGHT);
    }

    @Test
    @DisplayName("complete changes IN_FLIGHT record to COMPLETED")
    void complete_whenInFlight_thenChangeStatusToCompleted() {
        IdempotencyRecord record = IdempotencyRecord.inFlight("key-1", "hash-1", now, now.plusDays(1));

        record.complete("{\"paymentId\":\"payment-1\"}");

        assertThat(record.status()).isEqualTo(IdempotencyRecordStatus.COMPLETED);
        assertThat(record.responseBody()).isEqualTo("{\"paymentId\":\"payment-1\"}");
    }

    @Test
    @DisplayName("complete throws when record is already completed")
    void complete_whenAlreadyCompleted_thenThrowException() {
        IdempotencyRecord record = IdempotencyRecord.inFlight("key-1", "hash-1", now, now.plusDays(1));
        record.complete("{\"paymentId\":\"payment-1\"}");

        assertThatThrownBy(() -> record.complete("{\"paymentId\":\"payment-2\"}"))
                .isInstanceOf(DomainException.class);
    }
}
