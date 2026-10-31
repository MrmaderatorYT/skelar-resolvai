package com.skelar.defuse.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Customer {
    public String id;
    public String name;
    public String email;
    public String country;
    public String language;
    public String platform;          // web | ios | android
    public String signupAt;
    public double ltvUsd;
    @JsonProperty("contacts_last_30d")
    public int contactsLast30d;
}
