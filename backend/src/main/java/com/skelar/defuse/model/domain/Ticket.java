package com.skelar.defuse.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Ticket {
    public String id;
    public String customerId;
    public String channel;
    public String language;
    public String createdAt;
    public String text;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)  // read from seed, never serialized
    public GoldLabel gold;
    public String status = "open";   // open | resolved | needs_approval | escalated
}
