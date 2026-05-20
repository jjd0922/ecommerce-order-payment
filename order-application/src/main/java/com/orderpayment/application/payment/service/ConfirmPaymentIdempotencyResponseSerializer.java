package com.orderpayment.application.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ConfirmPaymentIdempotencyResponseSerializer {

    private final ObjectMapper objectMapper;

    public ConfirmPaymentIdempotencyResponseSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String serialize(ConfirmPaymentIdempotencyResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize confirm idempotency response", exception);
        }
    }

    ConfirmPaymentIdempotencyResponse deserialize(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, ConfirmPaymentIdempotencyResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize confirm idempotency response", exception);
        }
    }
}
