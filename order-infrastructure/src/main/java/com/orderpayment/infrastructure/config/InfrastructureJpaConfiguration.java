package com.orderpayment.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.orderpayment.infrastructure")
@EnableJpaRepositories(basePackages = "com.orderpayment.infrastructure")
public class InfrastructureJpaConfiguration {
}
