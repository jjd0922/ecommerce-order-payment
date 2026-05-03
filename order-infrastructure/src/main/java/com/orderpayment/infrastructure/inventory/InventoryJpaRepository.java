package com.orderpayment.infrastructure.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, String> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update InventoryJpaEntity inventory
            set inventory.availableQuantity = inventory.availableQuantity - :quantity,
                inventory.heldQuantity = inventory.heldQuantity + :quantity
            where inventory.productId = :productId
              and inventory.availableQuantity >= :quantity
            """)
    int hold(
            @Param("productId") String productId,
            @Param("quantity") int quantity
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update InventoryJpaEntity inventory
            set inventory.availableQuantity = inventory.availableQuantity + :quantity,
                inventory.heldQuantity = inventory.heldQuantity - :quantity
            where inventory.productId = :productId
              and inventory.heldQuantity >= :quantity
            """)
    int release(
            @Param("productId") String productId,
            @Param("quantity") int quantity
    );
}
