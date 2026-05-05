package com.orderpayment.infrastructure.event;

import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.domain.common.event.DomainEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingDomainEventPublisherAdapter implements DomainEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisherAdapter.class);

    @Override
    public void publishAll(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            log.info(
                    "domainEvent type={} aggregateId={} occurredAt={}",
                    event.eventType(),
                    event.aggregateId(),
                    event.occurredAt()
            );
        }
    }
}
