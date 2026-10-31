package com.skelar.defuse.service.llm;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record LlmSettings(
        @Value("${defuse.llm.mode:gold}") String mode,
        @Value("${defuse.llm.provider:gemma}") String provider,
        @Value("${defuse.llm.model:gemma-4-31b-it}") String model,
        @Value("${defuse.llm.api-key-env:GEMINI_API_KEY}") String apiKeyEnv,
        @Value("${defuse.llm.timeout-ms:15000}") int timeoutMs,
        @Value("${defuse.llm.max-retries:2}") int maxRetries,
        @Value("${defuse.llm.json-mode:false}") boolean jsonMode,
        @Value("${defuse.llm.response-schema:false}") boolean responseSchema) {
    public LlmSettings {
        if (!Set.of("gold", "live", "replay").contains(mode) || !"gemma".equals(provider)
                || model == null || model.isBlank() || timeoutMs < 100 || maxRetries < 0 || maxRetries > 5
                || apiKeyEnv == null || !apiKeyEnv.matches("[A-Z][A-Z0-9_]*"))
            throw new IllegalArgumentException("Invalid defuse.llm configuration");
    }
}
