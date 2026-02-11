package com.be_ai_learning_platform.security.secret;

public interface SecretStore {

    /**
     * Returns AI API key if exists and non-blank.
     * Throw IllegalStateException if empty (to keep current service behavior).
     */
    String requireAiApiKey();

    /**
     * Persist raw API key and make it effective immediately (hot).
     */
    void writeAiApiKey(String rawKey);

    /**
     * Returns masked key for UI display (never expose full key).
     * Example: abc****xyz
     */
    String maskAiApiKey();
}
