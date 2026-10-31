package com.skelar.defuse.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** What the LLM extracts from a ticket. The policy branches on the single intent and the flags;
 * the trailing optional fields (spec C) are informational and may be null. */
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
        List<Evidence> evidence,
        @JsonPropertyDescription("Only for cancel requests: too_expensive | no_time | not_using | cant_find_cancel | technical | other | none")
        String cancelReason,
        @JsonPropertyDescription("Every intent the ticket expresses; contains intent")
        List<String> intents,
        Boolean explicitCancel,
        Boolean explicitRefund,
        @JsonPropertyDescription("Mentions a bank, card issuer or chargeback; implies dispute_signal")
        Boolean bankEscalation,
        @JsonPropertyDescription("Facts needed to act that the text does not give")
        List<String> missingInformation) {

    public ClassificationFeatures(String intent, String trueGoal, boolean sarcasm, boolean hiddenAggression,
                                  boolean explicitAggression, boolean disputeSignal, boolean injection, String sentiment,
                                  String language, double confidence, List<Evidence> evidence, String cancelReason) {
        this(intent, trueGoal, sarcasm, hiddenAggression, explicitAggression, disputeSignal, injection, sentiment,
                language, confidence, evidence, cancelReason, null, null, null, null, null);
    }

    public ClassificationFeatures withInjection(boolean value) {
        return new ClassificationFeatures(intent, trueGoal, sarcasm, hiddenAggression, explicitAggression,
                disputeSignal, value, sentiment, language, confidence, evidence, cancelReason,
                intents, explicitCancel, explicitRefund, bankEscalation, missingInformation);
    }

    /** Safe default: zero confidence, so the policy never auto-executes. */
    public static ClassificationFeatures fallback(String language, String reason) {
        return new ClassificationFeatures("unclear", reason, false, false, false, false, false,
                "neutral", language, 0.0, List.of(), "none");
    }
}
