package com.orderpayment.infrastructure.inventory;

import com.orderpayment.domain.inventory.InventoryReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryReservationStatus status;

    protected InventoryReservationJpaEntity() {
    }

    public InventoryReservationJpaEntity(
            String id,
            String orderId,
            String productId,
            int quantity,
            LocalDateTime expiresAt,
            InventoryReservationStatus status
    ) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public String id() {
        return id;
    }

    public String orderId() {
        return orderId;
    }

    public String productId() {
        return productId;
    }

    public int quantity() {
        return quantity;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public InventoryReservationStatus status() {
        return status;
    }

    public void expire() {
        this.status = InventoryReservationStatus.EXPIRED;
    }
}
