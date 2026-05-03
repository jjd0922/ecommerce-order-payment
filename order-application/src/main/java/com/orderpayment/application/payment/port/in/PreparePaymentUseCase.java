package com.orderpayment.application.payment.port.in;

import com.orderpayment.application.payment.dto.PreparePaymentCommand;
import com.orderpayment.application.payment.dto.PreparePaymentResult;

public interface PreparePaymentUseCase {

    PreparePaymentResult prepare(PreparePaymentCommand command);
}
