package com.orderpayment.infrastructure.inventory;

import com.orderpayment.application.payment.dto.ExpireInventoryReservationsResult;
import com.orderpayment.application.payment.port.in.ExpireInventoryReservationsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReservationExpirationScheduler {

    private final ExpireInventoryReservationsUseCase expireInventoryReservationsUseCase;

    @Scheduled(
            initialDelayString = "${order-payment.inventory-reservation.expiration.initial-delay:60000}",
            fixedDelayString = "${order-payment.inventory-reservation.expiration.fixed-delay:60000}"
    )
    public void expireReservations() {
        ExpireInventoryReservationsResult result = expireInventoryReservationsUseCase.expire();
        if (result.expiredCount() > 0) {
            log.info("expired inventory reservations. expiredCount={}, processedAt={}",
                    result.expiredCount(),
                    result.processedAt()
            );
        }
    }
}
