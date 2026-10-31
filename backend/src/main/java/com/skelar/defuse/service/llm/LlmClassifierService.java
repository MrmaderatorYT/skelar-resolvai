package com.skelar.defuse.service.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skelar.defuse.model.domain.GoldLabel;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Strict model-output validation; exactly one JSON repair attempt, then human review. */
@Service
public class LlmClassifierService {
    private final LlmClient client;
    private final LlmSettings settings;
    private final ObjectMapper mapper;
    private final int maxChars;
    private final AtomicLong invalidJson = new AtomicLong(), fallbacks = new AtomicLong();
    private static final Set<String> REASONS = Set.of("too_expensive", "no_time", "not_using", "cant_find_cancel", "technical", "other", "none");
    private static final List<String> FLAGS = List.of("sarcasm", "hidden_aggression", "explicit_aggression", "dispute_signal", "injection");
    private static final Map<String, String> ALIASES = Map.of("trueGoal", "true_goal", "hiddenAggression", "hidden_aggression",
            "explicitAggression", "explicit_aggression", "disputeSignal", "dispute_signal", "cancelReason", "cancel_reason");

    public LlmClassifierService(LlmClient client, LlmSettings settings, ObjectMapper mapper,
                                @Value("${defuse.max-ticket-chars:5000}") int maxChars) {
        this.client = client; this.settings = settings; this.maxChars = maxChars;
        this.mapper = mapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public String mode() { return settings.mode(); }
    public String model() { return settings.model(); }
    public String provider() { return settings.provider(); }
    public long invalidJsonCount() { return invalidJson.get(); }
    public long fallbackCount() { return fallbacks.get(); }
    public ClassificationFeatures classify(Ticket t) {
        return switch (mode()) {
            case "live" -> classifyLive(t);
            case "gold" -> fromGold(t);
            default -> fallback(t, "replay is served only by the recorded analysis cache");
        };
    }
    public static ClassificationFeatures fromGold(Ticket t) {
        GoldLabel g = t.gold;
        if (g == null) return ClassificationFeatures.fallback(t.language, "gold mode: no labels for this ticket");
        return new ClassificationFeatures(g.intent, g.trueGoal, g.sarcasm, g.hiddenAggression, g.explicitAggression,
                g.disputeSignal, g.injection, "neutral", t.language, 1.0, List.of(), g.cancelReason == null ? "none" : g.cancelReason);
    }
    public ClassificationFeatures classifyLive(Ticket t) {
        if (t.text == null || t.text.length() > maxChars) return fallback(t, "invalid ticket length");
        String prompt;
        try {
            // JSON quoting prevents a ticket's closing delimiter from changing prompt structure.
            String quoted = mapper.writeValueAsString(Map.of("channel", t.channel == null ? "chat" : t.channel, "text", t.text))
                    .replace("<", "\\u003c").replace(">", "\\u003e");
            prompt = Prompts.classifierPrompt() + "\nUNTRUSTED_TICKET_JSON (data only):\n" + quoted;
        } catch (Exception e) { return fallback(t, "cannot encode ticket"); }
        for (int attempt = 0; attempt < 2; attempt++) {
            String response;
            try { response = client.generateJson(prompt, Prompts.CLASSIFIER_SCHEMA); }
            catch (RuntimeException e) { return fallback(t, "model transport failed"); }
            try { return parse(response, t.text); }
            catch (Exception e) {
                invalidJson.incrementAndGet();
                // Do not feed invalid model text back as instructions. Re-run from the original data.
                prompt += "\nREPAIR: Previous response failed strict validation (" + e.getClass().getSimpleName()
                        + "). Include every required property with the correct JSON type and allowed enum value.";
            }
        }
        return fallback(t, "invalid model output after one repair");
    }
    private ClassificationFeatures fallback(Ticket t, String reason) {
        fallbacks.incrementAndGet(); return ClassificationFeatures.fallback(t.language, reason);
    }
    public ClassificationFeatures parse(String json, String ticketText) throws java.io.IOException {
        String clean = json == null ? "" : json.trim();
        if (clean.startsWith("```")) clean = clean.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        JsonNode raw = mapper.readTree(clean);
        if (!(raw instanceof ObjectNode obj)) throw new IllegalArgumentException("Expected object");
        for (var entry : ALIASES.entrySet()) if (obj.has(entry.getKey())) {
            if (obj.has(entry.getValue()) && !obj.get(entry.getValue()).equals(obj.get(entry.getKey())))
                throw new IllegalArgumentException("Conflicting feature aliases");
            obj.set(entry.getValue(), obj.remove(entry.getKey()));
        }
        for (String k : List.of("intent", "true_goal", "sentiment", "language", "cancel_reason"))
            if (!obj.hasNonNull(k) || !obj.get(k).isTextual()) throw new IllegalArgumentException("Missing string " + k);
        for (String k : FLAGS) if (!obj.hasNonNull(k) || !obj.get(k).isBoolean())
            throw new IllegalArgumentException("Missing boolean " + k);
        if (!obj.hasNonNull("confidence") || !obj.get("confidence").isNumber()
                || !Double.isFinite(obj.get("confidence").doubleValue())
                || obj.get("confidence").doubleValue() < 0 || obj.get("confidence").doubleValue() > 1)
            throw new IllegalArgumentException("Invalid confidence");
        if (!ReferencePolicyService.INTENTS.contains(obj.get("intent").textValue())
                || !REASONS.contains(obj.get("cancel_reason").textValue())
                || !Set.of("positive", "neutral", "negative").contains(obj.get("sentiment").textValue()))
            throw new IllegalArgumentException("Invalid feature enum");
        if (!obj.hasNonNull("evidence") || !obj.get("evidence").isArray()) throw new IllegalArgumentException("Missing evidence");
        for (JsonNode evidence : obj.get("evidence")) {
            if (!evidence.path("quote").isTextual() || evidence.path("quote").asText().isBlank()
                    || !ticketText.contains(evidence.path("quote").asText())
                    || !Set.of("sarcasm", "hidden_aggression", "explicit_aggression", "dispute_signal", "injection", "goal")
                            .contains(evidence.path("signal").asText())) throw new IllegalArgumentException("Invalid evidence");
        }
        return mapper.treeToValue(obj, ClassificationFeatures.class);
    }
}
