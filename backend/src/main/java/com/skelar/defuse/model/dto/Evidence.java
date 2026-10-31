package com.skelar.defuse.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record Evidence(
        @JsonPropertyDescription("Exact quote from the ticket") String quote,
        @JsonPropertyDescription("sarcasm | hidden_aggression | explicit_aggression | dispute_signal | injection | goal") String signal) {
}
