package com.skelar.defuse.model.domain;

public class Payment {
    public String id;
    public String customerId;
    public double amount;
    public String currency;
    public String type;              // trial | renewal
    public String status;            // succeeded | refunded | charged_back | disputed
    public String processor;         // card | app_store | google_play
    public String createdAt;
    public Double refundedAmount;
    public String refundedAt;
}
