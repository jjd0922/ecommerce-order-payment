package com.orderpayment.api.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.idempotency.IdempotencyInFlightException;
import com.orderpayment.application.payment.IdempotencyKeyConflictException;
import com.orderpayment.application.payment.PaymentInProgressException;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.application.payment.port.in.ConfirmPaymentUseCase;
import com.orderpayment.application.payment.port.in.PreparePaymentUseCase;
import com.orderpayment.domain.common.DomainException;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PreparePaymentUseCase preparePaymentUseCase;

    @MockitoBean
    private ConfirmPaymentUseCase confirmPaymentUseCase;

    @Test
    @DisplayName("POST /v1/payments/prepare returns created payment")
    void preparePayment_whenRequestValid_thenReturnCreatedPayment() throws Exception {
        UUID paymentId = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(preparePaymentUseCase.prepare(any())).thenReturn(new PreparePaymentResult(
                new PaymentId(paymentId),
                new OrderId(orderId),
                Money.won(2000),
                OrderStatus.PAYMENT_PENDING,
                PaymentStatus.READY
        ));

        mockMvc.perform(post("/v1/payments/prepare")
                        .header("Idempotency-Key", "payment-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", orderId))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("Location", "/v1/payments/" + paymentId))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("READY"));
    }

    @Test
    @DisplayName("POST /v1/payments/{paymentId}/confirm returns approved payment")
    void confirmPayment_whenRequestValid_thenReturnApprovedPayment() throws Exception {
        UUID paymentId = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(confirmPaymentUseCase.confirm(any())).thenReturn(new ConfirmPaymentResult(
                new PaymentId(paymentId),
                new OrderId(orderId),
                Money.won(2000),
                OrderStatus.PAID,
                PaymentStatus.APPROVED
        ));

        mockMvc.perform(post("/v1/payments/{paymentId}/confirm", paymentId)
                        .header("X-Request-Id", "request-456")
                        .header("Idempotency-Key", "payment-request-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "request-456"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /v1/payments/prepare returns problem detail when idempotency key is missing")
    void preparePayment_whenIdempotencyKeyMissing_thenReturnBadRequest() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000701");

        mockMvc.perform(post("/v1/payments/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", orderId))))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.title").value("invalid request"));
    }

    @Test
    @DisplayName("POST /v1/payments/prepare returns 409 when idempotency key conflicts")
    void preparePayment_whenIdempotencyKeyConflicts_thenReturnConflict() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(preparePaymentUseCase.prepare(any()))
                .thenThrow(new IdempotencyKeyConflictException("idempotency key was already used"));

        mockMvc.perform(post("/v1/payments/prepare")
                        .header("X-Request-Id", "request-789")
                        .header("Idempotency-Key", "payment-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", orderId))))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"))
                .andExpect(jsonPath("$.requestId").value("request-789"));
    }

    @Test
    @DisplayName("POST /v1/payments/prepare returns 409 when idempotency request is in flight")
    void preparePayment_whenIdempotencyRequestIsInFlight_thenReturnConflict() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(preparePaymentUseCase.prepare(any()))
                .thenThrow(new IdempotencyInFlightException("idempotency request is in flight"));

        mockMvc.perform(post("/v1/payments/prepare")
                        .header("Idempotency-Key", "payment-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", orderId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_IN_FLIGHT"));
    }

    @Test
    @DisplayName("POST /v1/payments/prepare returns 422 when inventory is insufficient")
    void preparePayment_whenInventoryInsufficient_thenReturnUnprocessableEntity() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(preparePaymentUseCase.prepare(any()))
                .thenThrow(new DomainException("inventory is insufficient"));

        mockMvc.perform(post("/v1/payments/prepare")
                        .header("Idempotency-Key", "payment-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", orderId))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    @DisplayName("POST /v1/payments/{paymentId}/confirm returns 409 when payment is processing")
    void confirmPayment_whenPaymentIsProcessing_thenReturnConflict() throws Exception {
        UUID paymentId = UUID.fromString("00000000-0000-0000-0000-000000000601");
        when(confirmPaymentUseCase.confirm(any()))
                .thenThrow(new PaymentInProgressException("payment approval is still processing"));

        mockMvc.perform(post("/v1/payments/{paymentId}/confirm", paymentId)
                        .header("Idempotency-Key", "payment-request-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));
    }
}
