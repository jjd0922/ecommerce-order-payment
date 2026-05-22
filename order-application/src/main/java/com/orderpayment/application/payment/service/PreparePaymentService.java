package com.orderpayment.application.payment.service;

import com.orderpayment.application.idempotency.IdempotencyRecordAlreadyExistsException;
import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;
import com.orderpayment.application.payment.port.in.PreparePaymentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreparePaymentService implements PreparePaymentUseCase {

    private final PreparePaymentTransactionService transactionService;

    @Override
    public PreparePaymentResult prepare(PreparePaymentCommand command) {
        try {
            return transactionService.prepare(command);
        } catch (IdempotencyRecordAlreadyExistsException exception) {
            return transactionService.replayExisting(command);
        }
    }
}
