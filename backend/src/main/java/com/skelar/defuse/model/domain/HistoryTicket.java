package com.skelar.defuse.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One closed ticket from the synthetic human-only baseline month. */
public class HistoryTicket {
    public String id;
    public String customerId;
    public String language;
    public String createdAt;
    public String channel;
    public String textEn;
    public String rootCause;
    public String intent;
    public boolean sarcasm;
    public boolean hiddenAggression;
    public boolean explicitAggression;
    public boolean disputeSignal;
    @JsonProperty("contacts_last_30d")
    public int contactsLast30d;
    public double riskScore;
    public String riskBand;
    public String handledBy;
    public double firstResponseHours;
    public double resolutionHours;
    public double refundAmount;
    public Integer csat;
    public boolean chargebackFiledAfter;
    public boolean synthetic;
}
