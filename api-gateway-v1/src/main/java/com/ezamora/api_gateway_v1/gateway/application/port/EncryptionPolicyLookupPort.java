package com.ezamora.api_gateway_v1.gateway.application.port;

import java.util.Optional;

/**
 * Puerto de aplicación: resuelve una {@link PayloadEncryptionPolicyPort} a partir de la clave de configuración.
 */
public interface EncryptionPolicyLookupPort {

    Optional<PayloadEncryptionPolicyPort> findPolicy(String key);
}
