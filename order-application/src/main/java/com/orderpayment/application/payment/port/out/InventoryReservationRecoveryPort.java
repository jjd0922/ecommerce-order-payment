package com.orderpayment.application.payment.port.out;

import com.orderpayment.domain.inventory.InventoryReservation;
import java.time.LocalDateTime;
import java.util.List;

public interface InventoryReservationRecoveryPort {

    List<InventoryReservation> expireExpiredReservations(LocalDateTime now);
}
