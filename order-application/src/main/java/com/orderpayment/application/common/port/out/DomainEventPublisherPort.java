package com.orderpayment.application.common.port.out;

import com.orderpayment.domain.common.event.DomainEvent;
import java.util.List;

public interface DomainEventPublisherPort {

    void publishAll(List<DomainEvent> events);
}
