package com.skelar.defuse.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Billing facts the policy needs: what the agent's read-only tools return. */
public record CustomerContext(
        String customerId,
        String platform,
        String billingProcessor,   // card | app_store | google_play: routing uses this, not the device
        double ltvUsd,
        @JsonProperty("contacts_last_30d") int contactsLast30d,
        @JsonProperty("refunds_last_180d") int refundsLast180d,
        int chargebacksLifetime,
        String subscriptionStatus,
        String plan,
        double price,
        LastPayment lastPayment,
        String duplicatePaymentId,
        int sessionsSinceLastCharge,
        int cancelFlowAbandoned,
        // retention profile
        String acquisitionChannel,
        long tenureMonths,
        int paidRenewals,
        @JsonProperty("offers_12m") int offers12m,
        @JsonProperty("identity_offers_12m") int identityOffers12m,
        @JsonProperty("retention_offers_30d") int retentionOffers30d,
        boolean inHoldout) {
}
