package com.skelar.defuse.model.domain;

public class Subscription {
    public String id;
    public String customerId;
    public String plan;              // monthly | quarterly | annual
    public double price;
    public String currency;
    public String status;            // active | canceled | paused
    public String processor;
    public String currentPeriodEnd;
    public String canceledAt;
    public String pauseUntil;
}
