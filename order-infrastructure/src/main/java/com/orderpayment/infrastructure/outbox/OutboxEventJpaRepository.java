package com.orderpayment.infrastructure.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {

    @Query(
            value = """
                    SELECT *
                    FROM outbox_event
                    WHERE status = :status
                    ORDER BY occurred_at ASC, id ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<OutboxEventJpaEntity> findPendingForUpdateSkipLocked(
            @Param("status") String status,
            @Param("limit") int limit
    );
}
