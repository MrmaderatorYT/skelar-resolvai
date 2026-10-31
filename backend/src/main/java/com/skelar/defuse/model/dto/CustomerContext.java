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
        boolean inHoldout,
        CrossRail crossRail) {
    public CustomerContext(String customerId, String platform, String billingProcessor, double ltvUsd, int contactsLast30d,
        int refundsLast180d, int chargebacksLifetime, String subscriptionStatus, String plan, double price, LastPayment lastPayment,
        String duplicatePaymentId, int sessionsSinceLastCharge, int cancelFlowAbandoned, String acquisitionChannel, long tenureMonths,
        int paidRenewals, int offers12m, int identityOffers12m, int retentionOffers30d, boolean inHoldout) {
        this(customerId, platform, billingProcessor, ltvUsd, contactsLast30d, refundsLast180d, chargebacksLifetime, subscriptionStatus,
            plan, price, lastPayment, duplicatePaymentId, sessionsSinceLastCharge, cancelFlowAbandoned, acquisitionChannel, tenureMonths,
            paidRenewals, offers12m, identityOffers12m, retentionOffers30d, inHoldout, null);
    }
    public record CrossRail(String storeSubscriptionId, String storeStatus, String cardSubscriptionId, String cardStatus, String cardPaymentId) {}
}
