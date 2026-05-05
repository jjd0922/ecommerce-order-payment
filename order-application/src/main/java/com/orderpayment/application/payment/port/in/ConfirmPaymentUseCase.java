package com.orderpayment.application.payment.port.in;

import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import com.orderpayment.application.payment.dto.ConfirmPaymentResult;

public interface ConfirmPaymentUseCase {

    ConfirmPaymentResult confirm(ConfirmPaymentCommand command);
}
