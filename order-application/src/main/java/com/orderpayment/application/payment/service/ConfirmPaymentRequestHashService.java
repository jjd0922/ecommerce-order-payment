package com.orderpayment.application.payment.service;

import com.orderpayment.application.payment.dto.ConfirmPaymentCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ConfirmPaymentRequestHashService {

    public String hash(ConfirmPaymentCommand command) {
        String rawRequest = "confirm-payment:" + command.paymentId().value();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
