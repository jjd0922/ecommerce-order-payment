package com.orderpayment.infrastructure.inventory;

import com.orderpayment.domain.inventory.InventoryReservationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationJpaRepository extends JpaRepository<InventoryReservationJpaEntity, String> {

    List<InventoryReservationJpaEntity> findByOrderIdAndStatus(
            String orderId,
            InventoryReservationStatus status
    );

    List<InventoryReservationJpaEntity> findByStatusAndExpiresAtBefore(
            InventoryReservationStatus status,
            LocalDateTime expiresAt
    );
}
