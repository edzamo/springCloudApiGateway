package com.ezamora.api_gateway_v1.gateway.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ezamora.api_gateway_v1.gateway.application.service.EncryptionStrategyResolver;

@Configuration
public class GatewayConfiguration {

    @Bean
    public EncryptionStrategyResolver encryptionStrategyResolver() {
        return new EncryptionStrategyResolver();
    }
}
