package com.be_ai_learning_platform.security.secret;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class EncryptedFileSecretStore implements SecretStore {

    private static final Logger log = LoggerFactory.getLogger(EncryptedFileSecretStore.class);

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final ObjectMapper objectMapper;

    @Value("${app.secrets.aiKeyPath:./data/secrets/ai_api_key.json}")
    private String aiKeyPath;

    @Value("${app.secrets.masterKey:${APP_SECRET_MASTER_KEY:}}")
    private String masterKey;

    private volatile Map<String, String> cachedKeys = new LinkedHashMap<>();
    private volatile Map<String, String> cachedMasked = new LinkedHashMap<>();

    public EncryptedFileSecretStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        tryLoadFromDisk();
    }

    @Override
    public String requireAiApiKey() {
        return requireAiApiKey("GEMINI");
    }

    @Override
    public String requireAiApiKey(String provider) {
        String normalizedProvider = normalizeProvider(provider);

        String key = getCachedKey(normalizedProvider);
        if (key == null || key.isBlank()) {
            tryLoadFromDisk();
            key = getCachedKey(normalizedProvider);
        }

        if (key == null || key.isBlank()) {
            throw new IllegalStateException("AI api key is empty for provider: " + normalizedProvider);
        }

        return key.trim();
    }

    @Override
    public void writeAiApiKey(String rawKey) {
        writeAiApiKey("GEMINI", rawKey);
    }

    @Override
    public void writeAiApiKey(String provider, String rawKey) {
        if (rawKey == null || rawKey.trim().isBlank()) {
            throw new IllegalArgumentException("aiApiKey must not be blank");
        }

        ensureMasterKey();

        String normalizedProvider = normalizeProvider(provider);
        String key = rawKey.trim();

        try {
            Map<String, String> next = new LinkedHashMap<>(cachedKeys == null ? Map.of() : cachedKeys);
            next.put(normalizedProvider, key);
            persist(next);
            updateCaches(next);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write AI api key secret file", e);
        }
    }

    @Override
    public String maskAiApiKey() {
        return maskAiApiKey("GEMINI");
    }

    @Override
    public String maskAiApiKey(String provider) {
        String normalizedProvider = normalizeProvider(provider);

        String masked = cachedMasked == null ? null : cachedMasked.get(normalizedProvider);
        if (masked == null) {
            tryLoadFromDisk();
            masked = cachedMasked == null ? null : cachedMasked.get(normalizedProvider);
        }

        return masked;
    }

    @Override
    public void clearAiApiKey() {
        clearAiApiKey("GEMINI");
    }

    @Override
    public void clearAiApiKey(String provider) {
        String normalizedProvider = normalizeProvider(provider);

        try {
            Map<String, String> next = new LinkedHashMap<>(cachedKeys == null ? Map.of() : cachedKeys);
            next.remove(normalizedProvider);

            Path path = Paths.get(aiKeyPath).toAbsolutePath();

            if (next.isEmpty()) {
                if (Files.exists(path)) {
                    Files.delete(path);
                }
            } else {
                persist(next);
            }

            updateCaches(next);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clear AI api key secret file", e);
        }
    }

    private void tryLoadFromDisk() {
        try {
            Path path = Paths.get(aiKeyPath).toAbsolutePath();
            if (!Files.exists(path)) {
                return;
            }

            ensureMasterKey();

            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) {
                return;
            }

            SecretFilePayload payload = objectMapper.readValue(json, SecretFilePayload.class);
            if (payload == null || payload.ct == null || payload.iv == null) {
                return;
            }

            byte[] iv = Base64.getDecoder().decode(payload.iv);
            byte[] ct = Base64.getDecoder().decode(payload.ct);

            byte[] plain = decryptAesGcm(ct, iv);
            String decoded = new String(plain, StandardCharsets.UTF_8).trim();
            if (decoded.isBlank()) {
                return;
            }

            Map<String, String> loaded = decodeSecretMap(decoded);
            updateCaches(loaded);

        } catch (Exception ex) {
            log.warn("Failed to load AI secret store from disk: {}", ex.getMessage());
        }
    }

    private Map<String, String> decodeSecretMap(String decoded) {
        try {
            Map<String, String> map = objectMapper.readValue(
                    decoded,
                    new TypeReference<Map<String, String>>() {}
            );

            if (map == null || map.isEmpty()) {
                return new LinkedHashMap<>();
            }

            Map<String, String> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                normalized.put(normalizeProvider(entry.getKey()), entry.getValue().trim());
            }
            return normalized;

        } catch (Exception ignore) {
            Map<String, String> legacy = new LinkedHashMap<>();
            legacy.put("GEMINI", decoded.trim());
            return legacy;
        }
    }

    private void persist(Map<String, String> keyMap) throws Exception {
        Path path = Paths.get(aiKeyPath).toAbsolutePath();
        Path dir = path.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);

        String plainJson = objectMapper.writeValueAsString(keyMap);
        byte[] cipherText = encryptAesGcm(plainJson.getBytes(StandardCharsets.UTF_8), iv);

        SecretFilePayload payload = new SecretFilePayload();
        payload.v = 2;
        payload.alg = "AES/GCM/NoPadding";
        payload.iv = Base64.getEncoder().encodeToString(iv);
        payload.ct = Base64.getEncoder().encodeToString(cipherText);
        payload.updatedAt = LocalDateTime.now().toString();

        String json = objectMapper
                .copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(payload);

        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.writeString(
                tmp,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void updateCaches(Map<String, String> keyMap) {
        Map<String, String> normalized = new LinkedHashMap<>();
        Map<String, String> masked = new LinkedHashMap<>();

        if (keyMap != null) {
            for (Map.Entry<String, String> entry : keyMap.entrySet()) {
                String provider = normalizeProvider(entry.getKey());
                String key = entry.getValue() == null ? null : entry.getValue().trim();

                if (key == null || key.isBlank()) {
                    continue;
                }

                normalized.put(provider, key);
                masked.put(provider, mask(key));
            }
        }

        cachedKeys = normalized;
        cachedMasked = masked;
    }

    private String getCachedKey(String provider) {
        return cachedKeys == null ? null : cachedKeys.get(provider);
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "GEMINI";
        }
        return provider.trim().toUpperCase(Locale.ROOT);
    }

    private void ensureMasterKey() {
        if (masterKey == null || masterKey.trim().isBlank()) {
            throw new IllegalStateException(
                    "Master key is missing. Set ENV APP_SECRET_MASTER_KEY (or app.secrets.masterKey)."
            );
        }
    }

    private SecretKeySpec keySpec() {
        try {
            byte[] k = MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.trim().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(k, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES key from master key", e);
        }
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
        if (key == null || key.isBlank()) {
            return null;
        }

        String k = key.trim();
        if (k.length() <= 6) {
            return "***";
        }

        return k.substring(0, 3) + "****" + k.substring(k.length() - 3);
    }

    public static class SecretFilePayload {
        public Integer v;
        public String alg;
        public String iv;
        public String ct;
        public String updatedAt;
    }
}