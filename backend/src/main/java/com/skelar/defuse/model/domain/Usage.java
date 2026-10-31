package com.skelar.defuse.model.domain;

public class Usage {
    public String customerId;
    public int sessionsSinceLastCharge;
    public String lastActiveAt;
    public int streakDays;
    public int cancelFlowAbandoned; // started the cancel flow and gave up: evidence for disputes
}
