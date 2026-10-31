package com.skelar.defuse.model.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Full result of the pipeline for one ticket. Status, reply and executed change on approval. */
public class TicketAnalysis {
    public String ticketId;
    public ClassificationFeatures features;
    public InjectionCheck injectionGuard;
    public CustomerContext context;
    public PolicyDecision decision;
    public MoneyAtRisk money;
    public String reply;
    public String status;
    public long latencyMs;
    public List<Map<String, Object>> executed = new ArrayList<>();
}
