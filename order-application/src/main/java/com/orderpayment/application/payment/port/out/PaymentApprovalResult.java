package com.orderpayment.application.payment.port.out;

public record PaymentApprovalResult(
        boolean success,
        String pgTransactionId,
        String failureReason
) {

    public static PaymentApprovalResult approved(String pgTransactionId) {
        return new PaymentApprovalResult(true, pgTransactionId, null);
    }

    public static PaymentApprovalResult failed(String pgTransactionId, String failureReason) {
        return new PaymentApprovalResult(false, pgTransactionId, failureReason);
    }
}
