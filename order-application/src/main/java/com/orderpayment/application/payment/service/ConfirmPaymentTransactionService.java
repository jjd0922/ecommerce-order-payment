package com.orderpayment.application.payment.service;

import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.payment.PaymentInProgressException;
import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.InventoryReservationQueryPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentQueryPort;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.inventory.InventoryReservationConfirmedEvent;
import com.orderpayment.domain.inventory.InventoryReservationReleasedEvent;
import com.orderpayment.domain.order.Order;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.Payment;
import com.orderpayment.domain.payment.PaymentApprovedEvent;
import com.orderpayment.domain.payment.PaymentFailedEvent;
import com.orderpayment.domain.payment.PaymentStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfirmPaymentTransactionService {

    private final PaymentQueryPort paymentQueryPort;
    private final PaymentCommandPort paymentCommandPort;
    private final OrderQueryPort orderQueryPort;
    private final OrderCommandPort orderCommandPort;
    private final InventoryReservationQueryPort inventoryReservationQueryPort;
    private final InventoryReservationCommandPort inventoryReservationCommandPort;
    private final CurrentTimePort currentTimePort;
    private final DomainEventPublisherPort domainEventPublisherPort;
    private final ConfirmPaymentIdempotencyHandler idempotencyHandler;

    @Transactional
    public ConfirmPaymentAttempt beginApproval(ConfirmPaymentCommand command) {
        ConfirmPaymentIdempotencyDecision decision = idempotencyHandler.resolve(command);
        if (decision.hasReplayResult()) {
            return ConfirmPaymentAttempt.completed(decision.replayResult());
        }

        Payment payment = paymentQueryPort.getPaymentForUpdate(command.paymentId());

        Order order = orderQueryPort.getOrder(payment.orderId());
        if (payment.status() == PaymentStatus.PROCESSING) {
            throw new PaymentInProgressException("payment approval is still processing");
        }
        if (payment.status() != PaymentStatus.READY) {
            return ConfirmPaymentAttempt.completed(toResult(payment, order.status()), decision.inFlightRecord());
        }
        payment.startApproval(currentTimePort.now());
        paymentCommandPort.savePayment(payment);
        return ConfirmPaymentAttempt.readyForApproval(payment, decision.inFlightRecord());
    }

    @Transactional
    public ConfirmPaymentResult applyApprovalResult(Payment paymentForApproval, PaymentApprovalResult approvalResult) {
        Payment payment = paymentQueryPort.getPaymentForUpdate(paymentForApproval.id());
        Order order = orderQueryPort.getOrder(payment.orderId());
        if (payment.status() == PaymentStatus.APPROVED || payment.status() == PaymentStatus.FAILED) {
            return toResult(payment, order.status());
        }

        if (approvalResult.success()) {
            return approvePayment(payment, order, approvalResult.pgTransactionId());
        }
        return failPayment(payment, order, approvalResult.pgTransactionId(), approvalResult.failureReason());
    }

    @Transactional
    public void completeIdempotencyRecord(IdempotencyRecord record, ConfirmPaymentResult result) {
        idempotencyHandler.complete(record, result);
    }

    private ConfirmPaymentResult approvePayment(Payment payment, Order order, String pgTransactionId) {
        LocalDateTime occurredAt = currentTimePort.now();
        List<InventoryReservation> reservations = inventoryReservationQueryPort.findHeldReservationsByOrderId(order.id());
        List<DomainEvent> events = new ArrayList<>();

        payment.approve(pgTransactionId);
        order.markPaid();
        reservations.forEach(InventoryReservation::confirm);

        paymentCommandPort.savePayment(payment);
        orderCommandPort.saveOrder(order);
        inventoryReservationCommandPort.confirmAll(reservations);

        events.add(new PaymentApprovedEvent(payment.id(), order.id(), payment.amount(), occurredAt));
        reservations.forEach(reservation -> events.add(new InventoryReservationConfirmedEvent(
                reservation.id(),
                reservation.orderId(),
                reservation.productId(),
                reservation.quantity(),
                occurredAt
        )));
        domainEventPublisherPort.publishAll(events);

        return toResult(payment, order.status());
    }

    private ConfirmPaymentResult failPayment(Payment payment, Order order, String pgTransactionId, String failureReason) {
        LocalDateTime occurredAt = currentTimePort.now();
        List<InventoryReservation> reservations = inventoryReservationQueryPort.findHeldReservationsByOrderId(order.id());
        List<DomainEvent> events = new ArrayList<>();

        payment.fail(pgTransactionId);
        order.markFailed();
        reservations.forEach(InventoryReservation::release);

        paymentCommandPort.savePayment(payment);
        orderCommandPort.saveOrder(order);
        inventoryReservationCommandPort.releaseAll(reservations);

        events.add(new PaymentFailedEvent(payment.id(), order.id(), payment.amount(), failureReason, occurredAt));
        reservations.forEach(reservation -> events.add(new InventoryReservationReleasedEvent(
                reservation.id(),
                reservation.orderId(),
                reservation.productId(),
                reservation.quantity(),
                occurredAt
        )));
        domainEventPublisherPort.publishAll(events);

        return toResult(payment, order.status());
    }

    private static ConfirmPaymentResult toResult(Payment payment, OrderStatus orderStatus) {
        return new ConfirmPaymentResult(
                payment.id(),
                payment.orderId(),
                payment.amount(),
                orderStatus,
                payment.status()
        );
    }
}
