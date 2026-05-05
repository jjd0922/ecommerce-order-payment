package com.orderpayment.application.payment.service;

import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.payment.IdempotencyKeyConflictException;
import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.application.payment.port.in.PreparePaymentUseCase;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentIdGeneratorPort;
import com.orderpayment.application.payment.port.out.PaymentQueryPort;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.inventory.InventoryReservation;
import com.orderpayment.domain.inventory.InventoryReservedEvent;
import com.orderpayment.domain.order.Order;
import com.orderpayment.domain.order.OrderItem;
import com.orderpayment.domain.order.OrderStatus;
import com.orderpayment.domain.payment.Payment;
import com.orderpayment.domain.payment.PaymentPreparedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreparePaymentService implements PreparePaymentUseCase {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);

    private final OrderQueryPort orderQueryPort;
    private final OrderCommandPort orderCommandPort;
    private final InventoryReservationCommandPort inventoryReservationCommandPort;
    private final PaymentQueryPort paymentQueryPort;
    private final PaymentCommandPort paymentCommandPort;
    private final PaymentIdGeneratorPort paymentIdGeneratorPort;
    private final CurrentTimePort currentTimePort;
    private final DomainEventPublisherPort domainEventPublisherPort;

    @Override
    @Transactional
    public PreparePaymentResult prepare(PreparePaymentCommand command) {
        Order order = orderQueryPort.getOrder(command.orderId());

        return paymentQueryPort.findByIdempotencyKey(command.idempotencyKey())
                .map(payment -> getExistingResult(payment, order))
                .orElseGet(() -> prepareNewPayment(command, order));
    }

    private PreparePaymentResult getExistingResult(Payment payment, Order order) {
        if (!payment.orderId().equals(order.id()) || !payment.amount().equals(order.totalAmount())) {
            throw new IdempotencyKeyConflictException("idempotency key was already used with different payment request");
        }
        return toResult(payment, order.status());
    }

    private PreparePaymentResult prepareNewPayment(PreparePaymentCommand command, Order order) {
        LocalDateTime occurredAt = currentTimePort.now();
        LocalDateTime expiresAt = currentTimePort.now().plus(RESERVATION_TTL);
        List<DomainEvent> events = new ArrayList<>();
        for (OrderItem item : order.items()) {
            InventoryReservation reservation = inventoryReservationCommandPort.hold(
                    order.id(),
                    item.productId(),
                    item.quantity(),
                    expiresAt
            );
            events.add(new InventoryReservedEvent(
                    reservation.id(),
                    order.id(),
                    item.productId(),
                    item.quantity(),
                    expiresAt,
                    occurredAt
            ));
        }

        order.requestPayment();
        orderCommandPort.saveOrder(order);

        Payment payment = Payment.ready(
                paymentIdGeneratorPort.generate(),
                order.id(),
                order.totalAmount(),
                command.idempotencyKey()
        );
        paymentCommandPort.savePayment(payment);
        events.add(new PaymentPreparedEvent(
                payment.id(),
                order.id(),
                order.totalAmount(),
                command.idempotencyKey(),
                occurredAt
        ));
        domainEventPublisherPort.publishAll(events);

        return toResult(payment, order.status());
    }

    private static PreparePaymentResult toResult(Payment payment, OrderStatus orderStatus) {
        return new PreparePaymentResult(
                payment.id(),
                payment.orderId(),
                payment.amount(),
                orderStatus,
                payment.status()
        );
    }
}
