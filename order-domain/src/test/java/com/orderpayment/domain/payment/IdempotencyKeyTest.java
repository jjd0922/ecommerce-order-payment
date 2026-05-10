package com.orderpayment.domain.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.domain.common.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    @DisplayName("생성자는 빈 키이면 예외를 던진다")
    void constructor_whenKeyBlank_thenThrowException() {
        assertThatThrownBy(() -> new IdempotencyKey(" "))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("생성자는 키가 최대 길이를 초과하면 예외를 던진다")
    void constructor_whenKeyTooLong_thenThrowException() {
        assertThatThrownBy(() -> new IdempotencyKey("a".repeat(101)))
                .isInstanceOf(DomainException.class);
    }
}
