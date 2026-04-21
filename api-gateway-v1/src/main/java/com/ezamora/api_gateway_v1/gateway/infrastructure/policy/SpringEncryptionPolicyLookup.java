package com.ezamora.api_gateway_v1.gateway.infrastructure.policy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ezamora.api_gateway_v1.gateway.application.port.EncryptionPolicyLookupPort;
import com.ezamora.api_gateway_v1.gateway.application.port.PayloadEncryptionPolicyPort;

/**
 * Adaptador secundario (salida): resuelve implementaciones de {@link PayloadEncryptionPolicyPort} vía el contenedor Spring.
 */
@Component
public class SpringEncryptionPolicyLookup implements EncryptionPolicyLookupPort {

    private final Map<String, PayloadEncryptionPolicyPort> byKey;

    public SpringEncryptionPolicyLookup(List<PayloadEncryptionPolicyPort> policies) {
        this.byKey = policies.stream()
                .collect(Collectors.toMap(PayloadEncryptionPolicyPort::policyKey, Function.identity()));
    }

    @Override
    public Optional<PayloadEncryptionPolicyPort> findPolicy(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(key));
    }
}
