package com.orderpayment.application.payment.port.out;

public record PaymentApprovalResult(
        boolean success,
        String failureReason
) {

    public static PaymentApprovalResult approved() {
        return new PaymentApprovalResult(true, null);
    }

    public static PaymentApprovalResult failed(String failureReason) {
        return new PaymentApprovalResult(false, failureReason);
    }
}
