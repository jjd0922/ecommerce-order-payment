package com.orderpayment.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    @DisplayName("selling 은 상품명이 비어 있으면 예외를 던진다")
    void selling_whenNameBlank_thenThrowException() {
        assertThatThrownBy(() -> Product.selling(ProductId.newId(), " ", Money.won(1000)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("stopSelling 은 판매 상태를 중지하고 판매 검증을 실패시킨다")
    void stopSelling_whenCalled_thenStopSellingAndRejectEnsureSelling() {
        Product product = Product.selling(ProductId.newId(), "keyboard", Money.won(1000));

        product.stopSelling();

        assertThat(product.isSelling()).isFalse();
        assertThatThrownBy(product::ensureSelling)
                .isInstanceOf(DomainException.class);
    }
}
