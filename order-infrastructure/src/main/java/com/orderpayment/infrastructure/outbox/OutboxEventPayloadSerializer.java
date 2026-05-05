package com.orderpayment.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventPayloadSerializer {

    private final ObjectMapper objectMapper;

    public String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new DomainException("failed to serialize outbox event");
        }
    }
}
