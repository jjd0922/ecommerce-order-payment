package com.orderpayment.api.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.application.payment.port.in.ConfirmPaymentUseCase;
import com.orderpayment.application.payment.port.in.PreparePaymentUseCase;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.PaymentId;
import com.orderpayment.domain.payment.PaymentStatus;
import java.util.Map;
import java.util.UUID;
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
    void preparesPayment() throws Exception {
        UUID paymentId = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        given(preparePaymentUseCase.prepare(any())).willReturn(new PreparePaymentResult(
                new PaymentId(paymentId),
                new OrderId(orderId),
                Money.won(2000),
                OrderStatus.PAYMENT_PENDING,
                PaymentStatus.READY
        ));

        mockMvc.perform(post("/payments/prepare")
                        .header("Idempotency-Key", "payment-request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", orderId))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("Location", "/payments/" + paymentId))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.paymentStatus").value("READY"));
    }

    @Test
    void confirmsPayment() throws Exception {
        UUID paymentId = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        given(confirmPaymentUseCase.confirm(any())).willReturn(new ConfirmPaymentResult(
                new PaymentId(paymentId),
                new OrderId(orderId),
                Money.won(2000),
                OrderStatus.PAID,
                PaymentStatus.APPROVED
        ));

        mockMvc.perform(post("/payments/{paymentId}/confirm", paymentId)
                        .header("X-Request-Id", "request-456")
                        .header("Idempotency-Key", "payment-request-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "request-456"))
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"));
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000701");

        mockMvc.perform(post("/payments/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderId", orderId))))
                .andExpect(status().isBadRequest());
    }
}
