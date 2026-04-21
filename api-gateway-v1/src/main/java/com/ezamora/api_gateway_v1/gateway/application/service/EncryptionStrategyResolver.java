package com.ezamora.api_gateway_v1.gateway.application.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpMethod;

import com.ezamora.api_gateway_v1.gateway.application.support.GatewayRouteMetadata;

/**
 * Servicio de aplicación <strong>sin Spring</strong>: deduce la clave de política (RSA, AES, …) desde metadata
 * de ruta + método HTTP. Testeable con mapas en memoria.
 */
public final class EncryptionStrategyResolver {

    private static final String WILDCARD_METHOD = "*";

    public Optional<String> resolvePolicyKey(Map<String, Object> metadata, HttpMethod httpMethod) {
        if (metadata == null || metadata.isEmpty() || httpMethod == null) {
            return Optional.empty();
        }

        Optional<String> fromByMethod = resolveKeyFromStrategyByMethod(metadata, httpMethod);
        if (fromByMethod.isPresent()) {
            return sanitizeKey(fromByMethod.get());
        }

        Object raw = metadata.get(GatewayRouteMetadata.ROUTE_STRATEGY);
        if (raw == null || raw.toString().isBlank()) {
            return Optional.empty();
        }
        return sanitizeKey(raw.toString().trim());
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> resolveKeyFromStrategyByMethod(
            Map<String, Object> metadata,
            HttpMethod httpMethod) {

        Object raw = metadata.get(GatewayRouteMetadata.STRATEGY_BY_METHOD);
        if (!(raw instanceof Map)) {
            return Optional.empty();
        }
        Map<String, Object> byMethod = (Map<String, Object>) raw;
        if (byMethod.isEmpty()) {
            return Optional.empty();
        }
        String methodName = httpMethod.name();
        Object key = byMethod.get(methodName);
        if (key == null) {
            key = byMethod.get(WILDCARD_METHOD);
        }
        if (key == null) {
            return Optional.empty();
        }
        String s = key.toString().trim();
        return s.isEmpty() ? Optional.empty() : Optional.of(s);
    }

    private static Optional<String> sanitizeKey(String key) {
        if (isNoneOrSkip(key)) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    private static boolean isNoneOrSkip(String key) {
        return "NONE".equalsIgnoreCase(key) || "SKIP".equalsIgnoreCase(key);
    }
}
