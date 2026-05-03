package com.orderpayment.domain.payment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orderpayment.domain.common.DomainException;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void rejectsBlankKey() {
        assertThrows(DomainException.class, () -> new IdempotencyKey(" "));
    }

    @Test
    void rejectsTooLongKey() {
        assertThrows(DomainException.class, () -> new IdempotencyKey("a".repeat(101)));
    }
}
