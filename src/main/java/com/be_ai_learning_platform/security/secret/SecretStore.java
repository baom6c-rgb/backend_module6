package com.be_ai_learning_platform.security.secret;

public interface SecretStore {

    String requireAiApiKey();

    String requireAiApiKey(String provider);

    void writeAiApiKey(String rawKey);

    void writeAiApiKey(String provider, String rawKey);

    String maskAiApiKey();

    String maskAiApiKey(String provider);

    void clearAiApiKey();

    void clearAiApiKey(String provider);
}
