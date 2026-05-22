package com.orderpayment.application.payment.service;

import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;
import com.orderpayment.application.payment.port.in.ConfirmPaymentUseCase;
import com.orderpayment.application.payment.port.out.PaymentApprovalPort;
import com.orderpayment.application.payment.port.out.PaymentApprovalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfirmPaymentService implements ConfirmPaymentUseCase {

    private final PaymentApprovalPort paymentApprovalPort;
    private final ConfirmPaymentTransactionService transactionService;

    @Override
    public ConfirmPaymentResult confirm(ConfirmPaymentCommand command) {
        ConfirmPaymentAttempt attempt = transactionService.beginApproval(command);
        if (!attempt.requiresApproval()) {
            return attempt.completedResult();
        }

        PaymentApprovalResult approvalResult = paymentApprovalPort.approve(attempt.payment());
        return transactionService.applyApprovalResult(attempt.payment(), approvalResult);
    }
}
