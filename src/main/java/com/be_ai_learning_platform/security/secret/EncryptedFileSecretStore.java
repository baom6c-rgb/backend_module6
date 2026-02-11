package com.be_ai_learning_platform.security.secret;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class EncryptedFileSecretStore implements SecretStore {

    private static final int GCM_TAG_LENGTH_BITS = 128;  // standard
    private static final int IV_LENGTH_BYTES = 12;       // recommended for GCM

    private final ObjectMapper objectMapper;

    @Value("${app.secrets.aiKeyPath:./data/secrets/ai_api_key.json}")
    private String aiKeyPath;

    /**
     * Master key for encrypt/decrypt (must be kept secret).
     * Provide via ENV: APP_SECRET_MASTER_KEY (recommended).
     */
    @Value("${app.secrets.masterKey:${APP_SECRET_MASTER_KEY:}}")
    private String masterKey;

    private volatile String cachedKey;       // hot in-memory
    private volatile String cachedMasked;    // hot in-memory

    public EncryptedFileSecretStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // Try load existing secret file at startup (if present).
        tryLoadFromDisk();
    }

    @Override
    public String requireAiApiKey() {
        String k = cachedKey;
        if (k == null || k.isBlank()) {
            // fallback try load (in case file updated outside and cache not loaded yet)
            tryLoadFromDisk();
            k = cachedKey;
        }
        if (k == null || k.isBlank()) {
            throw new IllegalStateException("AI api key is empty");
        }
        return k.trim();
    }

    @Override
    public void writeAiApiKey(String rawKey) {
        if (rawKey == null || rawKey.trim().isBlank()) {
            throw new IllegalArgumentException("aiApiKey must not be blank");
        }
        ensureMasterKey();

        String key = rawKey.trim();

        try {
            Path path = Paths.get(aiKeyPath).toAbsolutePath();
            Path dir = path.getParent();
            if (dir != null) Files.createDirectories(dir);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            byte[] cipherText = encryptAesGcm(key.getBytes(StandardCharsets.UTF_8), iv);

            SecretFilePayload payload = new SecretFilePayload();
            payload.v = 1;
            payload.alg = "AES/GCM/NoPadding";
            payload.iv = Base64.getEncoder().encodeToString(iv);
            payload.ct = Base64.getEncoder().encodeToString(cipherText);
            payload.updatedAt = LocalDateTime.now().toString();

            String json = objectMapper
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);

            // atomic write
            Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            // update hot cache immediately
            cachedKey = key;
            cachedMasked = mask(key);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to write AI api key secret file", e);
        }
    }

    @Override
    public String maskAiApiKey() {
        String m = cachedMasked;
        if (m == null) {
            // try load to compute
            tryLoadFromDisk();
            m = cachedMasked;
        }
        return m;
    }

    // ===================== internals =====================

    private void tryLoadFromDisk() {
        try {
            Path path = Paths.get(aiKeyPath).toAbsolutePath();
            if (!Files.exists(path)) return;

            ensureMasterKey();

            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) return;

            SecretFilePayload payload = objectMapper.readValue(json, SecretFilePayload.class);
            if (payload == null || payload.ct == null || payload.iv == null) return;

            byte[] iv = Base64.getDecoder().decode(payload.iv);
            byte[] ct = Base64.getDecoder().decode(payload.ct);

            byte[] plain = decryptAesGcm(ct, iv);
            String key = new String(plain, StandardCharsets.UTF_8).trim();

            if (!key.isBlank()) {
                cachedKey = key;
                cachedMasked = mask(key);
            }
        } catch (Exception ignored) {
            // IMPORTANT: do not crash app if secret file is malformed.
            // Admin can overwrite key from UI to fix.
        }
    }

    private void ensureMasterKey() {
        if (masterKey == null || masterKey.trim().isBlank()) {
            throw new IllegalStateException("Master key is missing. Set ENV APP_SECRET_MASTER_KEY (or app.secrets.masterKey).");
        }
    }

    private SecretKeySpec keySpec() {
        // We derive a 256-bit key from the provided string.
        // Minimal approach: use bytes of UTF-8 + pad/trim to 32 bytes.
        // (Good enough for this project; if later you want stronger: use PBKDF2.)
        byte[] raw = masterKey.trim().getBytes(StandardCharsets.UTF_8);
        byte[] k = new byte[32];
        for (int i = 0; i < k.length; i++) {
            k[i] = raw[i % raw.length];
        }
        return new SecretKeySpec(k, "AES");
    }

    private byte[] encryptAesGcm(byte[] plain, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(), spec);
        return cipher.doFinal(plain);
    }

    private byte[] decryptAesGcm(byte[] cipherText, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec(), spec);
        return cipher.doFinal(cipherText);
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim();
        if (k.length() <= 6) return "***";
        return k.substring(0, 3) + "****" + k.substring(k.length() - 3);
    }

    // ===================== payload =====================

    public static class SecretFilePayload {
        public Integer v;
        public String alg;
        public String iv;
        public String ct;
        public String updatedAt;
    }
}
