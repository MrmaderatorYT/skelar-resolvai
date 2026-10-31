package com.skelar.defuse.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** start/end are computed by the server (UTF-16 offsets in the NFC ticket text), never taken from the model. */
public record Evidence(
        @JsonPropertyDescription("Exact quote from the ticket") String quote,
        @JsonPropertyDescription("sarcasm | hidden_aggression | explicit_aggression | dispute_signal | injection | bank_escalation | goal") String signal,
        Integer start,
        Integer end) {
    public Evidence(String quote, String signal) { this(quote, signal, null, null); }
}
