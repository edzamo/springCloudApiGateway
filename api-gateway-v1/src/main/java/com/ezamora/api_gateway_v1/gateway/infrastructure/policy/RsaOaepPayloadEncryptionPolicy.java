package com.ezamora.api_gateway_v1.gateway.infrastructure.policy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;

import org.springframework.stereotype.Component;

import com.ezamora.api_gateway_v1.gateway.application.port.PayloadEncryptionPolicyPort;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RsaOaepPayloadEncryptionPolicy implements PayloadEncryptionPolicyPort {

    private static final String KEY = "RSA";
    private static final String RSA_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private KeyPair keyPair;

    @PostConstruct
    void initializeKeys() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            keyPair = gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Inicialización de par de claves RSA", e);
        }
    }

    @Override
    public String policyKey() {
        return KEY;
    }

    @Override
    public void auditPlainPayload(byte[] plaintext) {
        if (plaintext.length == 0) {
            log.info("Auditoría RSA: cuerpo vacío, sin material que hashear.");
            return;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plaintext);
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
            byte[] cipherBytes = cipher.doFinal(digest);
            log.info(
                    "Auditoría RSA-OAEP (SHA-256 del cuerpo, {} bytes en claro). Cifrado (Base64): {}",
                    plaintext.length,
                    Base64.getEncoder().encodeToString(cipherBytes));
        } catch (Exception e) {
            log.warn("Auditoría RSA omitida por error (el reenvío del cuerpo no se altera): {}", e.toString());
        }
    }
}
