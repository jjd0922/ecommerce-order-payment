package com.orderpayment.application.payment.port.out;

import java.time.LocalDateTime;

public interface InventoryReservationRecoveryPort {

    int releaseExpiredReservations(LocalDateTime now);
}
