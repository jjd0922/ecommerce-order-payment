package com.orderpayment.infrastructure.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {

    List<OutboxEventJpaEntity> findTop100ByStatusOrderByOccurredAtAscIdAsc(OutboxEventStatus status);
}
