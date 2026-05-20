package com.orderpayment.application.payment.service;

import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.application.payment.port.in.ConfirmPaymentUseCase;
import com.orderpayment.application.payment.port.out.PaymentApprovalPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
public class ConfirmPaymentService implements ConfirmPaymentUseCase {

    private final PaymentApprovalPort paymentApprovalPort;
    private final ConfirmPaymentTransactionService transactionService;
    private final Counter confirmSuccessCounter;
    private final Counter confirmFailureCounter;

    public ConfirmPaymentService(
            PaymentApprovalPort paymentApprovalPort,
            ConfirmPaymentTransactionService transactionService,
            MeterRegistry meterRegistry
    ) {
        this.paymentApprovalPort = paymentApprovalPort;
        this.transactionService = transactionService;
        this.confirmSuccessCounter = Counter.builder("payment.confirm.success")
                .description("Number of successful payment confirmations")
                .register(meterRegistry);
        this.confirmFailureCounter = Counter.builder("payment.confirm.fail")
                .description("Number of failed payment confirmations")
                .register(meterRegistry);
    }

    @Override
    public ConfirmPaymentResult confirm(ConfirmPaymentCommand command) {
        ConfirmPaymentAttempt attempt = transactionService.beginApproval(command);
        if (!attempt.requiresApproval()) {
            if (attempt.inFlightRecord() != null) {
                transactionService.completeIdempotencyRecord(attempt.inFlightRecord(), attempt.completedResult());
            }
            return attempt.completedResult();
        }

        PaymentApprovalResult approvalResult = paymentApprovalPort.approve(attempt.payment());
        ConfirmPaymentResult result = transactionService.applyApprovalResult(attempt.payment(), approvalResult);
        transactionService.completeIdempotencyRecord(attempt.inFlightRecord(), result);
        if (approvalResult.success()) {
            confirmSuccessCounter.increment();
        } else {
            confirmFailureCounter.increment();
        }
        return result;
    }
}
