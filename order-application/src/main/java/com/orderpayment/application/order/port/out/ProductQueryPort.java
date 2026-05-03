package com.orderpayment.application.order.port.out;

import com.orderpayment.domain.product.Product;
import com.orderpayment.domain.product.ProductId;

public interface ProductQueryPort {

    Product getProduct(ProductId productId);
}
