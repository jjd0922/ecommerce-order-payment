package com.orderpayment.application.payment.dto;

import java.time.LocalDateTime;

public record RecoverProcessingPaymentsResult(
        int candidateCount,
        int recoveredCount,
        LocalDateTime processedAt
) {
}
