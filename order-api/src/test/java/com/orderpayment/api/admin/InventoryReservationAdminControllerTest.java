package com.orderpayment.api.admin;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderpayment.application.payment.dto.ExpireInventoryReservationsResult;
import com.orderpayment.application.payment.port.in.ExpireInventoryReservationsUseCase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryReservationAdminController.class)
class InventoryReservationAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpireInventoryReservationsUseCase expireInventoryReservationsUseCase;

    @Test
    void expiresInventoryReservations() throws Exception {
        LocalDateTime processedAt = LocalDateTime.of(2026, 5, 5, 10, 0);
        given(expireInventoryReservationsUseCase.expire())
                .willReturn(new ExpireInventoryReservationsResult(3, processedAt));

        mockMvc.perform(post("/admin/inventory-reservations/expire"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.expiredCount").value(3))
                .andExpect(jsonPath("$.processedAt").value("2026-05-05T10:00:00"));
    }
}
