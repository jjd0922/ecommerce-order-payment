package com.orderpayment.application.payment.service;

import com.orderpayment.application.common.port.out.CurrentTimePort;
import com.orderpayment.application.payment.dto.RecoverProcessingPaymentsResult;
import com.orderpayment.application.payment.port.in.RecoverProcessingPaymentsUseCase;
import com.orderpayment.application.payment.port.out.PaymentApprovalQueryPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import com.orderpayment.application.payment.port.out.PaymentQueryPort;
import com.orderpayment.domain.payment.Payment;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecoverProcessingPaymentsService implements RecoverProcessingPaymentsUseCase {

    private final PaymentQueryPort paymentQueryPort;
    private final PaymentApprovalQueryPort paymentApprovalQueryPort;
    private final ConfirmPaymentTransactionService transactionService;
    private final CurrentTimePort currentTimePort;
    private final Duration processingTimeout;
    private final int batchSize;

    public RecoverProcessingPaymentsService(
            PaymentQueryPort paymentQueryPort,
            PaymentApprovalQueryPort paymentApprovalQueryPort,
            ConfirmPaymentTransactionService transactionService,
            CurrentTimePort currentTimePort,
            @Value("${order-payment.payment.recovery.processing-timeout:PT5M}") Duration processingTimeout,
            @Value("${order-payment.payment.recovery.batch-size:100}") int batchSize
    ) {
        this.paymentQueryPort = paymentQueryPort;
        this.paymentApprovalQueryPort = paymentApprovalQueryPort;
        this.transactionService = transactionService;
        this.currentTimePort = currentTimePort;
        this.processingTimeout = processingTimeout;
        this.batchSize = batchSize;
    }

    @Override
    public RecoverProcessingPaymentsResult recover() {
        LocalDateTime now = currentTimePort.now();
        List<Payment> candidates = paymentQueryPort.findProcessingPaymentsRequestedBefore(
                now.minus(processingTimeout),
                batchSize
        );

        int recoveredCount = 0;
        for (Payment payment : candidates) {
            Optional<PaymentApprovalResult> approvalResult = paymentApprovalQueryPort.findApprovalResult(payment);
            if (approvalResult.isPresent()) {
                transactionService.applyApprovalResult(payment, approvalResult.get());
                recoveredCount++;
            }
        }
        return new RecoverProcessingPaymentsResult(candidates.size(), recoveredCount, now);
    }
}
