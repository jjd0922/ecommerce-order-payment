package com.orderpayment.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.orderpayment")
@EntityScan(basePackages = "com.orderpayment.infrastructure")
@EnableJpaRepositories(basePackages = "com.orderpayment.infrastructure")
public class OrderPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderPaymentApplication.class, args);
    }
}
