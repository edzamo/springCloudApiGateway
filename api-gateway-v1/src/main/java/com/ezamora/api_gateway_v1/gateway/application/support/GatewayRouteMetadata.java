package com.ezamora.api_gateway_v1.gateway.application.support;

/**
 * Claves de {@code metadata} en las rutas del gateway (contrato YAML ↔ capa de aplicación).
 */
public final class GatewayRouteMetadata {

    public static final String ROUTE_STRATEGY = "routeStrategy";
    public static final String STRATEGY_BY_METHOD = "strategyByMethod";

    private GatewayRouteMetadata() {}
}
