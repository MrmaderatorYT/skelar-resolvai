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
    // retention profile
    public String acquisitionChannel;   // meta_ads | tiktok_ads | google_ads | organic | referral | app_store_search
    public String funnel;               // web_quiz | ios_paywall | android_paywall
    public String quizGoal;             // sleep | stress | focus
    public String cardFingerprint;      // same card on several accounts = one identity cluster
    @JsonProperty("offers_12m")
    public int offers12m;
    public String lastOfferAt;
}
