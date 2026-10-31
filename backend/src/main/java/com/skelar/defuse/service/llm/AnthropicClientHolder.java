package com.skelar.defuse.service.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.stereotype.Component;

/** Lazily built client: gold mode and tests never need an API key. */
@Component
public class AnthropicClientHolder {
    private volatile AnthropicClient client;

    public AnthropicClient get() {
        if (client == null) {
            synchronized (this) {
                if (client == null) client = AnthropicOkHttpClient.fromEnv();
            }
        }
        return client;
    }
}
