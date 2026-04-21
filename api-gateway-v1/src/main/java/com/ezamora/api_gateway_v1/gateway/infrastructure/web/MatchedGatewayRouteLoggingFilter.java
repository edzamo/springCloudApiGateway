package com.ezamora.api_gateway_v1.gateway.infrastructure.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.ezamora.api_gateway_v1.gateway.application.port.EncryptionPolicyLookupPort;
import com.ezamora.api_gateway_v1.gateway.application.service.EncryptionStrategyResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Adaptador primario (entrada): registra ruta emparejada, upstream y política de payload efectiva.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchedGatewayRouteLoggingFilter implements GlobalFilter, Ordered {

    private final EncryptionStrategyResolver encryptionStrategyResolver;
    private final EncryptionPolicyLookupPort encryptionPolicyLookup;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null) {
            var method = exchange.getRequest().getMethod();
            String effectiveStrategy = encryptionStrategyResolver
                    .resolvePolicyKey(route.getMetadata(), method)
                    .flatMap(encryptionPolicyLookup::findPolicy)
                    .map(p -> p.policyKey())
                    .orElse("(sin estrategia de cifrado para este método)");
            log.info(
                    "Camino gateway → routeId={} | upstream={} | estrategiaEfectiva={} | {} {}",
                    route.getId(),
                    route.getUri(),
                    effectiveStrategy,
                    method,
                    exchange.getRequest().getURI().getPath());
        }
        return chain.filter(exchange);
    }
}
