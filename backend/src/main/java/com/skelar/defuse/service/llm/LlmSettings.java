package com.skelar.defuse.service.llm;

import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public record LlmSettings(String mode, String provider, String model, String apiKeyEnv, int timeoutMs, int maxRetries,
                          boolean jsonMode, boolean responseSchema, Generation classifier, Generation reply) {
    public LlmSettings {
        if (!Set.of("gold", "live", "replay").contains(mode) || !"gemma".equals(provider)
                || model == null || model.isBlank() || timeoutMs < 100 || maxRetries < 0 || maxRetries > 5
                || apiKeyEnv == null || !apiKeyEnv.matches("[A-Z][A-Z0-9_]*") || classifier == null || reply == null)
            throw new IllegalArgumentException("Invalid defuse.llm configuration");
    }

    /** Transport-only settings with the default generation settings. */
    public LlmSettings(String mode, String provider, String model, String apiKeyEnv, int timeoutMs, int maxRetries,
                       boolean jsonMode, boolean responseSchema) {
        this(mode, provider, model, apiKeyEnv, timeoutMs, maxRetries, jsonMode, responseSchema,
                Generation.CLASSIFIER, Generation.REPLY);
    }

    @Autowired
    public LlmSettings(@Value("${defuse.llm.mode:gold}") String mode,
                       @Value("${defuse.llm.provider:gemma}") String provider,
                       @Value("${defuse.llm.model:gemma-4-31b-it}") String model,
                       @Value("${defuse.llm.api-key-env:GEMINI_API_KEY}") String apiKeyEnv,
                       @Value("${defuse.llm.timeout-ms:15000}") int timeoutMs,
                       @Value("${defuse.llm.max-retries:2}") int maxRetries,
                       @Value("${defuse.llm.json-mode:false}") boolean jsonMode,
                       @Value("${defuse.llm.response-schema:false}") boolean responseSchema,
                       Environment env) {
        this(mode, provider, model, apiKeyEnv, timeoutMs, maxRetries, jsonMode, responseSchema,
                Generation.from(env, "defuse.llm.classifier", Generation.CLASSIFIER),
                Generation.from(env, "defuse.llm.reply", Generation.REPLY));
    }

    /** Sampling settings for one call. Temperature 0 with top-k 1 is greedy decoding: the same prompt gives
     * the same output, and the model never samples a less likely token, which is where invented facts come from. */
    public record Generation(float temperature, float topP, int topK, int seed, int maxOutputTokens, String thinkingLevel) {
        public static final Generation CLASSIFIER = new Generation(0.0f, 1.0f, 1, 7, 1024, "minimal");
        public static final Generation REPLY = new Generation(0.0f, 1.0f, 1, 7, 400, "minimal");

        public Generation {
            if (!(temperature >= 0 && temperature <= 2) || !(topP > 0 && topP <= 1) || topK < 1
                    || maxOutputTokens < 1 || maxOutputTokens > 8192
                    || thinkingLevel == null || !Set.of("minimal", "low", "medium", "high").contains(thinkingLevel))
                throw new IllegalArgumentException("Invalid defuse.llm generation settings");
        }

        static Generation from(Environment env, String prefix, Generation d) {
            return new Generation(env.getProperty(prefix + ".temperature", Float.class, d.temperature),
                    env.getProperty(prefix + ".top-p", Float.class, d.topP),
                    env.getProperty(prefix + ".top-k", Integer.class, d.topK),
                    env.getProperty(prefix + ".seed", Integer.class, d.seed),
                    env.getProperty(prefix + ".max-output-tokens", Integer.class, d.maxOutputTokens),
                    env.getProperty(prefix + ".thinking-level", d.thinkingLevel));
        }
    }
}
