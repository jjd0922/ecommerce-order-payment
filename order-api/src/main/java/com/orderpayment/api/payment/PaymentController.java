package com.orderpayment.api.payment;

import com.orderpayment.api.common.RequestTracing;
import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.application.payment.port.in.ConfirmPaymentUseCase;
import com.orderpayment.application.payment.port.in.PreparePaymentUseCase;
import com.orderpayment.domain.order.OrderId;
import com.orderpayment.domain.payment.IdempotencyKey;
import com.orderpayment.domain.payment.PaymentId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments")
public class PaymentController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final PreparePaymentUseCase preparePaymentUseCase;
    private final ConfirmPaymentUseCase confirmPaymentUseCase;

    @PostMapping("/prepare")
    public ResponseEntity<PaymentResponse> preparePayment(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody PreparePaymentRequest request
    ) {
        try {
            RequestTracing.putOrderId(request.orderId());
            PreparePaymentResult result = preparePaymentUseCase.prepare(new PreparePaymentCommand(
                    new OrderId(request.orderId()),
                    new IdempotencyKey(idempotencyKey)
            ));
            RequestTracing.putPaymentId(result.paymentId().value());
            return ResponseEntity.created(URI.create("/v1/payments/" + result.paymentId().value()))
                    .body(PaymentResponse.from(result));
        } finally {
            RequestTracing.removeOrderId();
            RequestTracing.removePaymentId();
        }
    }

    @PostMapping("/{paymentId}/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @PathVariable UUID paymentId,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey
    ) {
        try {
            RequestTracing.putPaymentId(paymentId);
            ConfirmPaymentResult result = confirmPaymentUseCase.confirm(new ConfirmPaymentCommand(
                    new PaymentId(paymentId),
                    new IdempotencyKey(idempotencyKey)
            ));
            RequestTracing.putOrderId(result.orderId().value());
            return ResponseEntity.ok(PaymentResponse.from(result));
        } finally {
            RequestTracing.removeOrderId();
            RequestTracing.removePaymentId();
        }
    }

    public record PreparePaymentRequest(@NotNull UUID orderId) {
    }

    public record PaymentResponse(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            String orderStatus,
            String paymentStatus
    ) {

        private static PaymentResponse from(PreparePaymentResult result) {
            return new PaymentResponse(
                    result.paymentId().value(),
                    result.orderId().value(),
                    result.amount().amount(),
                    result.orderStatus().name(),
                    result.paymentStatus().name()
            );
        }

        private static PaymentResponse from(ConfirmPaymentResult result) {
            return new PaymentResponse(
                    result.paymentId().value(),
                    result.orderId().value(),
                    result.amount().amount(),
                    result.orderStatus().name(),
                    result.paymentStatus().name()
            );
        }
    }
}
