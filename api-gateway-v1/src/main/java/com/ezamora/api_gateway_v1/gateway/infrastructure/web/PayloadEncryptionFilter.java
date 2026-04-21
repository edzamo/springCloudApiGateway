package com.ezamora.api_gateway_v1.gateway.infrastructure.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ezamora.api_gateway_v1.gateway.application.port.EncryptionPolicyLookupPort;
import com.ezamora.api_gateway_v1.gateway.application.port.PayloadEncryptionPolicyPort;
import com.ezamora.api_gateway_v1.gateway.application.service.EncryptionStrategyResolver;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Adaptador primario (entrada): aplica el flujo de auditoría de payload usando puertos y el servicio de aplicación.
 */
@Component
@RequiredArgsConstructor
public class PayloadEncryptionFilter implements GlobalFilter, Ordered {

    private final EncryptionStrategyResolver encryptionStrategyResolver;
    private final EncryptionPolicyLookupPort encryptionPolicyLookup;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        if (method == null) {
            return chain.filter(exchange);
        }

        Route matchedRoute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (matchedRoute == null) {
            return chain.filter(exchange);
        }

        return encryptionStrategyResolver
                .resolvePolicyKey(matchedRoute.getMetadata(), method)
                .flatMap(encryptionPolicyLookup::findPolicy)
                .map(policy -> applyPolicy(exchange, chain, policy, method))
                .orElseGet(() -> chain.filter(exchange));
    }

    private Mono<Void> applyPolicy(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            PayloadEncryptionPolicyPort policy,
            HttpMethod method) {

        if (methodHasHttpBody(method)) {
            return readBodyAuditAndForward(exchange, chain, policy);
        }

        policy.auditPlainPayload(new byte[0]);
        return chain.filter(exchange);
    }

    private static boolean methodHasHttpBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private Mono<Void> readBodyAuditAndForward(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            PayloadEncryptionPolicyPort policy) {

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    policy.auditPlainPayload(bytes);

                    ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            if (bytes.length == 0) {
                                return Flux.empty();
                            }
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                        }
                    };

                    return chain.filter(exchange.mutate().request(decorated).build());
                });
    }
}
