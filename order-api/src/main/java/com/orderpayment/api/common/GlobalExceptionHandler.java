package com.orderpayment.api.common;

import com.orderpayment.application.payment.IdempotencyKeyConflictException;
import com.orderpayment.domain.common.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyKeyConflict(IdempotencyKeyConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("IDEMPOTENCY_KEY_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_REQUEST", "invalid request"));
    }
}
