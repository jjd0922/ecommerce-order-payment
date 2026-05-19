package com.orderpayment.infrastructure.inventory;

import com.orderpayment.domain.inventory.InventoryReservationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationJpaRepository extends JpaRepository<InventoryReservationJpaEntity, String> {

    List<InventoryReservationJpaEntity> findByOrderIdAndStatus(
            String orderId,
            InventoryReservationStatus status
    );

    @Query(
            value = """
                    SELECT *
                    FROM inventory_reservation
                    WHERE status = :status
                      AND expires_at <= :expiresAt
                    ORDER BY expires_at ASC, id ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<InventoryReservationJpaEntity> findExpiredForUpdateSkipLocked(
            @Param("status") String status,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("limit") int limit
    );
}
