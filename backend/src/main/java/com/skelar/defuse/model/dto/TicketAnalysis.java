package com.skelar.defuse.model.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Full result of the pipeline for one ticket. Status, reply and executed change on approval. */
public class TicketAnalysis {
    public String ticketId;
    public String analysisId;
    public ClassificationFeatures features;
    public InjectionCheck injectionGuard;
    public CustomerContext context;
    public PolicyDecision decision;
    public com.skelar.defuse.service.policy.ReferencePolicyService.Autonomy autonomy;
    public MoneyAtRisk money;
    public String reply;
    public String status;
    public long latencyMs;
    // Additive provenance: callers retain all existing fields and REST paths.
    public String llmMode;
    public String llmProvider;
    public String llmModel;
    public String recordedAt;
    public List<Map<String, Object>> executed = new ArrayList<>();
}
