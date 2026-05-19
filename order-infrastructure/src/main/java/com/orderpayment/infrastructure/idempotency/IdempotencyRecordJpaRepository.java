package com.orderpayment.infrastructure.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordJpaRepository extends JpaRepository<IdempotencyRecordJpaEntity, String> {
}
