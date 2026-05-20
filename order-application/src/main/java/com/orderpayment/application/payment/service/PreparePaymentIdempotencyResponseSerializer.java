package com.orderpayment.application.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PreparePaymentIdempotencyResponseSerializer {

    private final ObjectMapper objectMapper;

    public PreparePaymentIdempotencyResponseSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(PreparePaymentIdempotencyResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize idempotency response", exception);
        }
    }

    public PreparePaymentIdempotencyResponse deserialize(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, PreparePaymentIdempotencyResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize idempotency response", exception);
        }
    }
}
