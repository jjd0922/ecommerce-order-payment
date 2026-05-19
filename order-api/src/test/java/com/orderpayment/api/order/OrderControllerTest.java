package com.orderpayment.api.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpayment.application.order.dto.CreateOrderResult;
import com.orderpayment.application.order.port.in.CreateOrderUseCase;
import com.orderpayment.domain.common.Money;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.order.OrderStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @Test
    @DisplayName("POST /orders 는 주문을 생성하고 생성 결과를 반환한다")
    void createOrder_whenRequestValid_thenReturnCreatedOrder() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000501");
        when(createOrderUseCase.create(any())).thenReturn(new CreateOrderResult(
                new OrderId(orderId),
                Money.won(2000),
                OrderStatus.CREATED
        ));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderLines", List.of(Map.of(
                                        "productId", productId,
                                        "quantity", 2
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("Location", "/orders/" + orderId))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.totalAmount").value(2000.00))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @DisplayName("POST /orders 는 요청이 유효하지 않으면 400 응답을 반환한다")
    void createOrder_whenRequestInvalid_thenReturnBadRequest() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("X-Request-Id", "request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("orderLines", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(header().string("X-Request-Id", "request-123"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.title").value("invalid request"))
                .andExpect(jsonPath("$.detail").value("invalid request"))
                .andExpect(jsonPath("$.requestId").value("request-123"));
    }
}
