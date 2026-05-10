package com.orderpayment.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("won 은 정수 금액을 소수점 둘째 자리 스케일로 생성한다")
    void won_whenIntegerAmount_thenCreateMoneyWithScale() {
        Money money = Money.won(1000);

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("won 은 음수 금액이면 예외를 던진다")
    void won_whenNegativeAmount_thenThrowException() {
        assertThatThrownBy(() -> Money.won(-1))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("add 와 multiply 는 금액 합산 후 배수를 계산한다")
    void addAndMultiply_whenCalled_thenCalculateResult() {
        Money result = Money.won(1000).add(Money.won(500)).multiply(2);

        assertThat(result).isEqualTo(Money.won(3000));
    }
}
