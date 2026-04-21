package com.ezamora.api_gateway_v1.gateway.application.port;

/**
 * Puerto de aplicación (salida): política de auditoría/transformación sobre el payload en claro.
 * Las implementaciones viven en {@code infrastructure.policy}.
 */
public interface PayloadEncryptionPolicyPort {

    /** Clave referenciada en YAML ({@code routeStrategy} / {@code strategyByMethod}). */
    String policyKey();

    void auditPlainPayload(byte[] plaintext);
}
