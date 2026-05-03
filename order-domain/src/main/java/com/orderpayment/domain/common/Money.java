package com.orderpayment.domain.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() < 0) {
            throw new DomainException("money must not be negative");
        }
    }

    public static Money won(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money multiply(int multiplier) {
        if (multiplier < 0) {
            throw new DomainException("multiplier must not be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(multiplier)));
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }
}
