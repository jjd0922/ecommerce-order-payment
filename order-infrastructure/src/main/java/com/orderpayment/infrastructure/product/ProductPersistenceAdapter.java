package com.orderpayment.infrastructure.product;

import com.orderpayment.application.order.port.out.ProductQueryPort;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.product.Product;
import com.orderpayment.domain.product.ProductId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductQueryPort {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product getProduct(ProductId productId) {
        return productJpaRepository.findById(productId.value().toString())
                .map(this::toDomain)
                .orElseThrow(() -> new DomainException("product not found"));
    }

    private Product toDomain(ProductJpaEntity entity) {
        return Product.restore(
                new ProductId(UUID.fromString(entity.id())),
                entity.name(),
                new Money(entity.price()),
                entity.selling()
        );
    }
}
