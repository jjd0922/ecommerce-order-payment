package com.orderpayment.domain.common.event;

import java.time.LocalDateTime;

public interface DomainEvent {

    String eventType();

    String aggregateId();

    LocalDateTime occurredAt();
}
