package com.ezamora.api_gateway_v1.gateway.infrastructure.policy;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ezamora.api_gateway_v1.gateway.application.port.PayloadEncryptionPolicyPort;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AesGcmPayloadEncryptionPolicy implements PayloadEncryptionPolicyPort {

    private static final String KEY = "AES";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmPayloadEncryptionPolicy(
            @Value("${api-gateway.body-encryption.aes-secret}") String secretUtf8) {
        byte[] keyBytes = secretUtf8.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "api-gateway.body-encryption.aes-secret debe medir 16, 24 o 32 bytes en UTF-8.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String policyKey() {
        return KEY;
    }

    @Override
    public void auditPlainPayload(byte[] plaintext) {
        if (plaintext.length == 0) {
            log.info("Auditoría AES-GCM: cuerpo vacío, sin material que cifrar.");
            return;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext);
            String ivB64 = Base64.getEncoder().encodeToString(iv);
            String ctB64 = Base64.getEncoder().encodeToString(cipherBytes);
            String preview = ctB64.length() > 96 ? ctB64.substring(0, 96) + "..." : ctB64;
            log.info(
                    "Auditoría AES-GCM ({} bytes en claro). IV (Base64): {} | Texto cifrado (Base64, extracto): {}",
                    plaintext.length,
                    ivB64,
                    preview);
        } catch (Exception e) {
            log.warn("Auditoría AES-GCM omitida por error (el reenvío del cuerpo no se altera): {}", e.toString());
        }
    }
}
