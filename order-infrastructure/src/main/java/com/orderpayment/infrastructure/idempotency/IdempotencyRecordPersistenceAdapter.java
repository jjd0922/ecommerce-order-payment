package com.orderpayment.infrastructure.idempotency;

import com.orderpayment.application.idempotency.IdempotencyRecordAlreadyExistsException;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdempotencyRecordPersistenceAdapter
        implements IdempotencyRecordCommandPort, IdempotencyRecordQueryPort {

    private final IdempotencyRecordJpaRepository idempotencyRecordJpaRepository;

    @Override
    public void save(IdempotencyRecord record) {
        try {
            idempotencyRecordJpaRepository.saveAndFlush(toEntity(record));
        } catch (DataIntegrityViolationException exception) {
            throw new IdempotencyRecordAlreadyExistsException("idempotency record already exists", exception);
        }
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        return idempotencyRecordJpaRepository.findById(key)
                .map(IdempotencyRecordPersistenceAdapter::toDomain);
    }

    private static IdempotencyRecordJpaEntity toEntity(IdempotencyRecord record) {
        return new IdempotencyRecordJpaEntity(
                record.key(),
                record.requestHash(),
                record.responseBody(),
                record.status(),
                record.createdAt(),
                record.expiresAt()
        );
    }

    private static IdempotencyRecord toDomain(IdempotencyRecordJpaEntity entity) {
        return IdempotencyRecord.restore(
                entity.key(),
                entity.requestHash(),
                entity.responseBody(),
                entity.status(),
                entity.createdAt(),
                entity.expiresAt()
        );
    }
}
