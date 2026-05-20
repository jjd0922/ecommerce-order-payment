package com.orderpayment.infrastructure.payment;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, String> {

    Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);

    @Query(
            value = """
                    SELECT *
                    FROM payment
                    WHERE status = :status
                      AND approval_requested_at < :approvalRequestedAt
                    ORDER BY approval_requested_at ASC, id ASC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<PaymentJpaEntity> findProcessingRequestedBefore(
            @Param("status") String status,
            @Param("approvalRequestedAt") LocalDateTime approvalRequestedAt,
            @Param("limit") int limit
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentJpaEntity payment where payment.id = :id")
    Optional<PaymentJpaEntity> findByIdForUpdate(@Param("id") String id);
}
