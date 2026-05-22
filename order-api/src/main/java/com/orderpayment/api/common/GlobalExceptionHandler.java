package com.orderpayment.api.common;

import com.orderpayment.application.common.InfrastructureException;
import com.orderpayment.application.idempotency.IdempotencyInFlightException;
import com.orderpayment.application.payment.IdempotencyKeyConflictException;
import com.orderpayment.application.payment.PaymentInProgressException;
import com.orderpayment.domain.common.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyKeyConflict(IdempotencyKeyConflictException exception) {
        return handle(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IdempotencyInFlightException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyInFlight(IdempotencyInFlightException exception) {
        return handle(ErrorCode.IDEMPOTENCY_IN_FLIGHT, exception.getMessage());
    }

    @ExceptionHandler(PaymentInProgressException.class)
    public ResponseEntity<ProblemDetail> handlePaymentInProgress(PaymentInProgressException exception) {
        return handle(ErrorCode.PAYMENT_IN_PROGRESS, exception.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(DomainException exception) {
        if (exception.getMessage() != null && exception.getMessage().contains("insufficient")) {
            return handle(ErrorCode.UNPROCESSABLE_ENTITY, exception.getMessage());
        }
        return handle(ErrorCode.DOMAIN_RULE_VIOLATION, exception.getMessage());
    }

    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<ProblemDetail> handleInfrastructureException(InfrastructureException exception) {
        return handle(ErrorCode.INFRASTRUCTURE_ERROR, exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ProblemDetail> handleInvalidRequest(Exception exception) {
        return handle(ErrorCode.INVALID_REQUEST, "invalid request");
    }

    private static ResponseEntity<ProblemDetail> handle(ErrorCode errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problemDetail.setTitle(errorCode.title());
        problemDetail.setProperty("code", errorCode.name());
        problemDetail.setProperty("requestId", RequestTracing.currentRequestId());
        return ResponseEntity.status(errorCode.status())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .body(problemDetail);
    }
}
