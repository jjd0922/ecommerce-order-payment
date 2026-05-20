package com.orderpayment.application.payment.service;

import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.common.port.out.DomainEventPublisherPort;
import com.orderpayment.application.idempotency.IdempotencyInFlightException;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordCommandPort;
import com.orderpayment.application.idempotency.port.out.IdempotencyRecordQueryPort;
import com.orderpayment.application.order.port.out.OrderCommandPort;
import com.orderpayment.application.order.port.out.OrderQueryPort;
import com.orderpayment.application.payment.IdempotencyKeyConflictException;
import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.application.payment.port.out.InventoryReservationCommandPort;
import com.orderpayment.application.payment.port.out.PaymentCommandPort;
import com.orderpayment.application.payment.port.out.PaymentIdGeneratorPort;
import com.orderpayment.domain.common.event.DomainEvent;
import com.orderpayment.domain.idempotency.IdempotencyRecord;
import com.orderpayment.domain.idempotency.IdempotencyRecordStatus;
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
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreparePaymentTransactionService {

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(1);

    private final OrderQueryPort orderQueryPort;
    private final OrderCommandPort orderCommandPort;
    private final InventoryReservationCommandPort inventoryReservationCommandPort;
    private final PaymentCommandPort paymentCommandPort;
    private final PaymentIdGeneratorPort paymentIdGeneratorPort;
    private final CurrentTimePort currentTimePort;
    private final DomainEventPublisherPort domainEventPublisherPort;
    private final IdempotencyRecordQueryPort idempotencyRecordQueryPort;
    private final IdempotencyRecordCommandPort idempotencyRecordCommandPort;
    private final PreparePaymentRequestHashService requestHashService;
    private final PreparePaymentIdempotencyResponseSerializer responseSerializer;

    @Transactional
    public PreparePaymentResult prepare(PreparePaymentCommand command) {
        String requestHash = requestHashService.hash(command);
        return idempotencyRecordQueryPort.findByKey(command.idempotencyKey().value())
                .map(record -> replay(record, requestHash))
                .orElseGet(() -> prepareNewPayment(command, requestHash));
    }

    @Transactional(readOnly = true)
    public PreparePaymentResult replayExisting(PreparePaymentCommand command) {
        String requestHash = requestHashService.hash(command);
        IdempotencyRecord record = idempotencyRecordQueryPort.findByKey(command.idempotencyKey().value())
                .orElseThrow(() -> new IdempotencyInFlightException("idempotency request is in flight"));
        return replay(record, requestHash);
    }

    private PreparePaymentResult replay(IdempotencyRecord record, String requestHash) {
        if (!record.hasSameRequestHash(requestHash)) {
            throw new IdempotencyKeyConflictException("idempotency key was already used with different payment request");
        }
        if (record.status() == IdempotencyRecordStatus.IN_FLIGHT) {
            throw new IdempotencyInFlightException("idempotency request is in flight");
        }
        return responseSerializer.deserialize(record.responseBody()).toResult();
    }

    private PreparePaymentResult prepareNewPayment(PreparePaymentCommand command, String requestHash) {
        LocalDateTime now = currentTimePort.now();
        IdempotencyRecord record = IdempotencyRecord.inFlight(
                command.idempotencyKey().value(),
                requestHash,
                now,
                now.plus(IDEMPOTENCY_TTL)
        );
        idempotencyRecordCommandPort.save(record);

        Order order = orderQueryPort.getOrder(command.orderId());
        PreparePaymentResult result = prepareNewPayment(command, order);
        record.complete(responseSerializer.serialize(PreparePaymentIdempotencyResponse.from(result)));
        idempotencyRecordCommandPort.save(record);
        return result;
    }

    private PreparePaymentResult prepareNewPayment(PreparePaymentCommand command, Order order) {
        LocalDateTime occurredAt = currentTimePort.now();
        LocalDateTime expiresAt = currentTimePort.now().plus(RESERVATION_TTL);
        List<DomainEvent> events = new ArrayList<>();
        for (OrderItem item : order.items().stream()
                .sorted(Comparator.comparing(item -> item.productId().value()))
                .toList()) {
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
