package com.skelar.defuse.service.llm;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Schema;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/** Official SDK: https://github.com/googleapis/java-genai (timeouts in milliseconds).
 * Instructions use the initial user turn, compatible with Gemma 3 and Gemma 4.
 * JSON/schema flags default off until the account-specific API probe verifies support.
 */
@Component
public class GemmaClient implements LlmClient {
    private final LlmSettings settings;
    private volatile Client client;

    public GemmaClient(LlmSettings settings) { this.settings = settings; }

    private Client client() {
        if (client == null) synchronized (this) {
            if (client == null) {
                String key = System.getenv(settings.apiKeyEnv());
                if (key == null || key.isBlank()) throw new IllegalStateException("Missing " + settings.apiKeyEnv());
                client = Client.builder().apiKey(key).httpOptions(HttpOptions.builder()
                        .timeout(settings.timeoutMs())
                        .retryOptions(HttpRetryOptions.builder().attempts(settings.maxRetries() + 1)
                                .httpStatusCodes(429, 500, 502, 503, 504).build()).build()).build();
            }
        }
        return client;
    }

    @Override public String generateJson(String prompt, String schemaHint) {
        var config = GenerateContentConfig.builder().temperature(0.0f).maxOutputTokens(4096);
        if (settings.jsonMode()) config.responseMimeType("application/json");
        if (settings.responseSchema()) config.responseSchema(Schema.fromJson(schemaHint));
        return nonempty(client().models.generateContent(settings.model(),
                prompt + "\nReturn one JSON object, no Markdown. JSON schema hint:\n" + schemaHint, config.build()).text());
    }

    @Override public String generateText(String prompt) {
        return nonempty(client().models.generateContent(settings.model(), prompt,
                GenerateContentConfig.builder().temperature(0.0f).maxOutputTokens(2048).build()).text());
    }

    private String nonempty(String text) {
        if (text == null || text.isBlank()) throw new IllegalStateException("Model returned no text");
        return text;
    }

    @PreDestroy public void close() { if (client != null) client.close(); }
}
