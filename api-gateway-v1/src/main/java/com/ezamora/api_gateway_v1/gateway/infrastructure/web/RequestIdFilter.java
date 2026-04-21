package com.ezamora.api_gateway_v1.gateway.infrastructure.web;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Adaptador primario (entrada): {@link GlobalFilter} para correlación y trazas vía {@code X-Request-ID}.
 */
@Slf4j
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest incoming = exchange.getRequest();
        String requestId = UUID.randomUUID().toString();
        String client = incoming.getRemoteAddress() != null
                ? incoming.getRemoteAddress().getAddress().getHostAddress()
                : "desconocido";

        log.info("Petición al gateway: {} {}", incoming.getMethod(), incoming.getURI().getPath());
        log.info("Request ID: {}", requestId);
        log.info("Cliente: {}", client);

        ServerHttpRequest request = incoming
                .mutate()
                .header("X-Request-ID", requestId)
                .build();

        ServerWebExchange updated = exchange.mutate().request(request).build();
        long startNanos = System.nanoTime();

        return chain.filter(updated).doFinally(signal -> {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            var status = updated.getResponse().getStatusCode();
            log.info("Respuesta hacia el cliente: {} | {} ms | Request ID: {}",
                    status != null ? status.value() : "?", elapsedMs, requestId);
        });
    }
}
