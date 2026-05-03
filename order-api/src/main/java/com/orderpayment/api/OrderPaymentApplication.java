package com.orderpayment.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.orderpayment")
public class OrderPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderPaymentApplication.class, args);
    }
}
