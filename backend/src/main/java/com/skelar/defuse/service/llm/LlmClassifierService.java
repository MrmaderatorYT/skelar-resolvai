package com.skelar.defuse.service.llm;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.skelar.defuse.model.domain.GoldLabel;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Ticket -> features. live: Claude Haiku with structured output; gold: eval labels, no API. */
@Service
public class LlmClassifierService {
    private static final Logger log = LoggerFactory.getLogger(LlmClassifierService.class);

    private final AnthropicClientHolder clients;
    private final String mode;
    private final String model;
    private final int maxChars;

    public LlmClassifierService(AnthropicClientHolder clients,
                                @Value("${defuse.llm.mode}") String mode,
                                @Value("${defuse.llm.classifier-model}") String model,
                                @Value("${defuse.max-ticket-chars}") int maxChars) {
        this.clients = clients;
        this.mode = mode;
        this.model = model;
        this.maxChars = maxChars;
    }

    public String mode() {
        return mode;
    }

    public ClassificationFeatures classify(Ticket t) {
        return "live".equals(mode) ? classifyLive(t) : fromGold(t);
    }

    public static ClassificationFeatures fromGold(Ticket t) {
        GoldLabel g = t.gold;
        if (g == null) return ClassificationFeatures.fallback(t.language, "gold mode: no labels for this ticket");
        return new ClassificationFeatures(g.intent, g.trueGoal, g.sarcasm, g.hiddenAggression, g.explicitAggression,
                g.disputeSignal, g.injection, "neutral", t.language, 1.0, List.of());
    }

    public ClassificationFeatures classifyLive(Ticket t) {
        String text = t.text.length() > maxChars ? t.text.substring(0, maxChars) : t.text;
        try {
            StructuredMessageCreateParams<ClassificationFeatures> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(2000L)
                    .system(Prompts.CLASSIFIER_SYSTEM)
                    .outputConfig(ClassificationFeatures.class)
                    .addUserMessage("<ticket channel=\"" + t.channel + "\">\n" + text + "\n</ticket>")
                    .build();
            return clients.get().messages().create(params).content().stream()
                    .flatMap(cb -> cb.text().stream())
                    .map(typed -> typed.text())
                    .findFirst()
                    .orElse(ClassificationFeatures.fallback(t.language, "classifier returned no output"));
        } catch (RuntimeException e) {   // API, network, refusal or schema errors -> human review
            log.warn("classifier failed for {}: {}", t.id, e.toString());
            return ClassificationFeatures.fallback(t.language, "classifier error: " + e.getClass().getSimpleName());
        }
    }
}
