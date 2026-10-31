package com.skelar.defuse.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** What the LLM extracts from a ticket. The policy reads only these fields. */
public record ClassificationFeatures(
        @JsonPropertyDescription("cancel | refund | cancel_refund | duplicate_charge | pause | technical_issue | account_access | account_security | question | billing_question | unclear")
        String intent,
        @JsonPropertyDescription("One sentence: what the customer really wants")
        String trueGoal,
        boolean sarcasm,
        boolean hiddenAggression,
        boolean explicitAggression,
        boolean disputeSignal,
        boolean injection,
        @JsonPropertyDescription("Literal surface tone, ignoring sarcasm: positive | neutral | negative")
        String sentiment,
        @JsonPropertyDescription("ISO 639-1 code, e.g. en, uk, de, es, pl")
        String language,
        @JsonPropertyDescription("0..1, how sure you are of the intent")
        double confidence,
        List<Evidence> evidence) {

    public ClassificationFeatures withInjection(boolean value) {
        return new ClassificationFeatures(intent, trueGoal, sarcasm, hiddenAggression, explicitAggression,
                disputeSignal, value, sentiment, language, confidence, evidence);
    }

    /** Safe default: zero confidence, so the policy never auto-executes. */
    public static ClassificationFeatures fallback(String language, String reason) {
        return new ClassificationFeatures("unclear", reason, false, false, false, false, false,
                "neutral", language, 0.0, List.of());
    }
}
