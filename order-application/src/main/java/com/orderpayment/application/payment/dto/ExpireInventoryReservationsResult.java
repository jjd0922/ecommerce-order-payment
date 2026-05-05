package com.orderpayment.application.payment.dto;

import java.time.LocalDateTime;

public record ExpireInventoryReservationsResult(
        int expiredCount,
        LocalDateTime processedAt
) {
}
