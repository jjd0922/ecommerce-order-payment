package com.orderpayment.api.admin;

import com.orderpayment.application.payment.dto.ExpireInventoryReservationsResult;
import com.orderpayment.application.payment.port.in.ExpireInventoryReservationsUseCase;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/inventory-reservations")
public class InventoryReservationAdminController {

    private final ExpireInventoryReservationsUseCase expireInventoryReservationsUseCase;

    @PostMapping("/expire")
    public ResponseEntity<ExpireInventoryReservationsResponse> expireReservations() {
        ExpireInventoryReservationsResult result = expireInventoryReservationsUseCase.expire();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ExpireInventoryReservationsResponse.from(result));
    }

    public record ExpireInventoryReservationsResponse(
            int expiredCount,
            LocalDateTime processedAt
    ) {

        private static ExpireInventoryReservationsResponse from(ExpireInventoryReservationsResult result) {
            return new ExpireInventoryReservationsResponse(result.expiredCount(), result.processedAt());
        }
    }
}
