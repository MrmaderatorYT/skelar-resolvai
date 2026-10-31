package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;

import com.skelar.defuse.model.domain.GoldLabel;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.llm.LlmClassifierService;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The Java policy must reproduce every gold decision of defuse-data/reference_policy.py. */
@SpringBootTest
class ReferencePolicyServiceTest {
    @Autowired InMemoryStore store;
    @Autowired ContextBuilderService contexts;
    @Autowired ReferencePolicyService policy;

    @BeforeEach
    void reset() {
        store.reset();
    }

    static Stream<String> ticketIds() {
        return Stream.iterate(1, i -> i <= 45, i -> i + 1).map(i -> "T%02d".formatted(i));
    }

    private PolicyDecision decide(String id, double confidence) {
        Ticket t = store.tickets.get(id);
        return policy.decide(LlmClassifierService.fromGold(t), contexts.build(t.customerId), confidence);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ticketIds")
    void matchesGold(String id) {
        GoldLabel g = store.tickets.get(id).gold;
        PolicyDecision d = decide(id, 1.0);
        assertThat(d.actions()).as("actions").isEqualTo(g.actions.stream().sorted().toList());
        assertThat(d.mode()).as("mode").isEqualTo(g.mode);
        assertThat(d.refundAmount()).as("refund").isEqualTo(g.refundAmount);
        assertThat(d.riskBand()).as("risk band").isEqualTo(g.riskBand);
        assertThat(d.priority()).as("priority").isEqualTo(g.priority);
    }

    @Test
    void injectionNeverExecutesActions() {
        for (String id : new String[] {"T29", "T30", "T31"}) {
            PolicyDecision d = decide(id, 1.0);
            assertThat(d.mode()).isEqualTo("escalate");
            assertThat(d.actions()).isEmpty();
        }
    }

    @Test
    void openDisputeIsNeverRefunded() {
        PolicyDecision d = decide("T37", 1.0);
        assertThat(d.refundAmount()).isZero();
        assertThat(d.actions()).containsExactly("evidence_pack");
    }

    @Test
    void lowConfidenceDowngradesAutoToSuggest() {
        assertThat(decide("T01", 0.5).mode()).isEqualTo("escalate");
    }

    @Test
    void invalidLlmOutputFailsClosed() {
        Ticket t = store.tickets.get("T01");
        var ctx = contexts.build(t.customerId);
        var bogus = new ClassificationFeatures("refund_everything", "", false, false, false, false, false,
                "neutral", "en", 1.0, java.util.List.of(), "none");
        assertThat(policy.decide(bogus, ctx, 1.0).mode()).isEqualTo("escalate");
        assertThat(policy.decide(LlmClassifierService.fromGold(t), ctx, Double.NaN).mode()).isEqualTo("escalate");
    }

    // ---- Codex review regressions (CODEX_REVIEW.md §6) ----

    private com.skelar.defuse.model.dto.CustomerContext ctxWith(String id, java.util.function.UnaryOperator<com.skelar.defuse.model.dto.CustomerContext> edit) {
        return edit.apply(contexts.build(store.tickets.get(id).customerId));
    }

    private static com.skelar.defuse.model.dto.CustomerContext copy(com.skelar.defuse.model.dto.CustomerContext c, String processor,
            double amount, String status, long days, int refunds, int chargebacks) {
        return new com.skelar.defuse.model.dto.CustomerContext(c.customerId(), c.platform(), processor, c.ltvUsd(),
                c.contactsLast30d(), refunds, chargebacks, c.subscriptionStatus(), c.plan(), c.price(),
                new com.skelar.defuse.model.dto.LastPayment(c.lastPayment().id(), amount, status, days),
                c.duplicatePaymentId(), c.sessionsSinceLastCharge(), c.cancelFlowAbandoned(), c.acquisitionChannel(),
                c.tenureMonths(), c.paidRenewals(), c.offers12m(), c.identityOffers12m(), c.retentionOffers30d(), c.inHoldout());
    }

    private static com.skelar.defuse.model.dto.CustomerContext onPlatform(com.skelar.defuse.model.dto.CustomerContext c, String platform) {
        return new com.skelar.defuse.model.dto.CustomerContext(c.customerId(), platform, c.billingProcessor(), c.ltvUsd(),
                c.contactsLast30d(), c.refundsLast180d(), c.chargebacksLifetime(), c.subscriptionStatus(), c.plan(), c.price(),
                c.lastPayment(), c.duplicatePaymentId(), c.sessionsSinceLastCharge(), c.cancelFlowAbandoned(), c.acquisitionChannel(),
                c.tenureMonths(), c.paidRenewals(), c.offers12m(), c.identityOffers12m(), c.retentionOffers30d(), c.inHoldout(), c.crossRail());
    }

    private ReferencePolicyService policyWithGate(String riskStatus, boolean autoRefunds, boolean sandbox) {
        var base = com.skelar.defuse.config.policy.PolicyProperties.defaults();
        var g = base.gate();
        var gate = new com.skelar.defuse.config.policy.PolicyProperties.Gate(g.autoErrorMax(), g.assistErrorMax(), g.autoImpactMaxUsd(),
                g.assistImpactMaxUsd(), g.autoExpectedLossMaxUsd(), g.priors(), g.minAutoConfidence(), g.minAssistConfidence(),
                riskStatus, autoRefunds, sandbox);
        return new ReferencePolicyService(new com.skelar.defuse.config.policy.PolicyProperties(base.refund(), base.retention(),
                base.risk(), base.money(), gate, base.sources()));
    }

    @Test
    void autoRefundLimitIsFiftyDollarsInclusive() {   // $50.00 runs on its own, $50.01 waits for a human
        var f = LlmClassifierService.fromGold(store.tickets.get("T07"));
        PolicyDecision at = policy.decide(f, ctxWith("T07", c -> copy(c, "card", 50.00, "succeeded", c.lastPayment().daysAgo(), 0, 0)), 1);
        assertThat(at.mode()).isEqualTo("auto");
        assertThat(at.refundAmount()).isEqualTo(50.00);
        PolicyDecision over = policy.decide(f, ctxWith("T07", c -> copy(c, "card", 50.01, "succeeded", c.lastPayment().daysAgo(), 0, 0)), 1);
        assertThat(over.mode()).isEqualTo("suggest");
        assertThat(over.reasons()).contains("above_auto_refund_limit");
    }

    @Test
    void uncalibratedRefundIsHeldOutsideTheSandbox() {   // spec E4: UNVALIDATED refunds run on their own only in the mock sandbox
        var f = LlmClassifierService.fromGold(store.tickets.get("T01"));
        var ctx = contexts.build(store.tickets.get("T01").customerId);
        assertThat(policy.decide(f, ctx, 1).mode()).isEqualTo("auto");
        PolicyDecision held = policyWithGate("UNVALIDATED", true, false).decide(f, ctx, 1);
        assertThat(held.mode()).isEqualTo("suggest");
        assertThat(held.actions()).containsExactlyInAnyOrder("cancel", "refund_full");
        assertThat(held.reasons()).anyMatch(r -> r.startsWith("risk_not_calibrated:"));
        assertThat(policyWithGate("VALIDATED", true, false).decide(f, ctx, 1).mode()).isEqualTo("auto");
        assertThat(policyWithGate("UNVALIDATED", true, true).decide(f, ctx, 1).mode()).isEqualTo("auto");
    }

    @Test
    void autoRefundSwitchOffHoldsEveryRefund() {
        var f = LlmClassifierService.fromGold(store.tickets.get("T01"));
        var ctx = contexts.build(store.tickets.get("T01").customerId);
        PolicyDecision off = policyWithGate("VALIDATED", false, true).decide(f, ctx, 1);
        assertThat(off.mode()).isEqualTo("suggest");
        assertThat(off.reasons()).anyMatch(r -> r.startsWith("risk_not_calibrated:"));
    }

    @Test
    void unknownBillingRailEscalatesAndIsNeverInferredFromTheDevice() {   // null processor escalates; Apple Pay on a web card is card
        var f = LlmClassifierService.fromGold(store.tickets.get("T01"));
        PolicyDecision none = policy.decide(f, ctxWith("T01", c -> copy(c, null, 49.99, "succeeded", c.lastPayment().daysAgo(), 0, 0)), 1);
        assertThat(none.mode()).isEqualTo("escalate");
        assertThat(none.actions()).isEmpty();
        assertThat(none.reasons()).contains("unknown_billing_processor");
        PolicyDecision iosCard = policy.decide(f, ctxWith("T01", c -> copy(onPlatform(c, "ios"), "card", 49.99, "succeeded", c.lastPayment().daysAgo(), 0, 0)), 1);
        assertThat(iosCard.actions()).contains("refund_full").noneMatch(a -> a.equals("store_refund_instructions"));
    }

    @Test
    void alreadyRefundedPaymentIsNeverRefundedTwice() {
        var f = LlmClassifierService.fromGold(store.tickets.get("T01"));
        PolicyDecision d = policy.decide(f, ctxWith("T01", c -> copy(c, "card", 49.99, "refunded", c.lastPayment().daysAgo(), 0, 0)), 1);
        assertThat(d.refundAmount()).isZero();
        assertThat(d.actions()).noneMatch(a -> a.startsWith("refund_"));
        assertThat(d.reasons()).anyMatch(r -> r.startsWith("already_refunded:"));
    }

    @Test
    void secondAccountOnTheSameCardInheritsTheCooldownOfCus0040() {   // identity cluster by card fingerprint
        assertThat(store.customer("cus_0041").cardFingerprint).isEqualTo(store.customer("cus_0040").cardFingerprint);
        assertThat(contexts.build("cus_0041").identityOffers12m()).isEqualTo(1);
        assertThat(contexts.build("cus_0040").identityOffers12m()).isZero();
    }

    @Test
    void duplicateRefundGoesThroughLimitAndHistoryGates() {   // F1, F2
        var f = LlmClassifierService.fromGold(store.tickets.get("T32"));
        assertThat(policy.decide(f, ctxWith("T32", c -> copy(c, "card", 89.99, "succeeded", 0, 0, 0)), 1).mode()).isEqualTo("suggest");
        assertThat(policy.decide(f, ctxWith("T32", c -> copy(c, "card", 29.99, "succeeded", 0, 1, 0)), 1).mode()).isEqualTo("suggest");
        assertThat(policy.decide(f, ctxWith("T32", c -> copy(c, "card", 29.99, "succeeded", 0, 0, 1)), 1).mode()).isEqualTo("suggest");
    }

    @Test
    void nonSucceededOrFuturePaymentIsNeverRefunded() {   // F3, F4
        var f = LlmClassifierService.fromGold(store.tickets.get("T01"));
        for (String status : new String[] {"refunded", "charged_back", "failed"}) {
            PolicyDecision d = policy.decide(f, ctxWith("T01", c -> copy(c, "card", 49.99, status, 2, 0, 0)), 1);
            assertThat(d.refundAmount()).as(status).isZero();
            assertThat(d.actions()).as(status).noneMatch(a -> a.startsWith("refund"));
        }
        assertThat(policy.decide(f, ctxWith("T01", c -> copy(c, "card", 49.99, "succeeded", -1, 0, 0)), 1).refundAmount()).isZero();
    }

    @Test
    void routingUsesBillingProcessorNotDevice() {   // F6, F7
        var refund = LlmClassifierService.fromGold(store.tickets.get("T01"));
        assertThat(policy.decide(refund, ctxWith("T01", c -> copy(c, "app_store", 49.99, "succeeded", 2, 0, 0)), 1).actions())
                .contains("store_refund_instructions").noneMatch(a -> a.startsWith("refund"));
        var pause = LlmClassifierService.fromGold(store.tickets.get("T34"));
        assertThat(policy.decide(pause, ctxWith("T34", c -> copy(c, "google_play", 29.99, "succeeded", 18, 0, 0)), 1).mode())
                .isEqualTo("escalate");
    }

    @Test
    void retentionOfferOnlyForEligibleCustomers() {
        var f = LlmClassifierService.fromGold(store.tickets.get("T39"));
        var base = contexts.build(store.tickets.get("T39").customerId);
        assertThat(policy.decide(f, base, 1).actions()).containsExactly("cancel", "offer_discount");   // cancel first, then one win-back
        java.util.function.Function<com.skelar.defuse.model.dto.CustomerContext, String> why = c -> policy.retentionOffer(f, c)[1];
        var c = base;
        assertThat(why.apply(new com.skelar.defuse.model.dto.CustomerContext(c.customerId(), c.platform(), c.billingProcessor(), c.ltvUsd(),
                c.contactsLast30d(), c.refundsLast180d(), c.chargebacksLifetime(), c.subscriptionStatus(), c.plan(), c.price(),
                c.lastPayment(), c.duplicatePaymentId(), c.sessionsSinceLastCharge(), c.cancelFlowAbandoned(), c.acquisitionChannel(),
                1, c.paidRenewals(), 0, 0, 0, false))).isEqualTo("tenure");
        assertThat(why.apply(new com.skelar.defuse.model.dto.CustomerContext(c.customerId(), c.platform(), c.billingProcessor(), c.ltvUsd(),
                c.contactsLast30d(), c.refundsLast180d(), c.chargebacksLifetime(), c.subscriptionStatus(), c.plan(), c.price(),
                c.lastPayment(), c.duplicatePaymentId(), c.sessionsSinceLastCharge(), c.cancelFlowAbandoned(), c.acquisitionChannel(),
                c.tenureMonths(), c.paidRenewals(), 0, 0, policy.properties().retention().maxOffersPer30d(), false))).isEqualTo("monthly_offer_budget");
        // a cancel_refund request never gets an offer: money questions go the refund path
        var refund = new ClassificationFeatures("cancel_refund", "", false, false, false, false, false, "neutral", "en", 1.0,
                java.util.List.of(), "too_expensive");
        assertThat(policy.decide(refund, base, 1).actions()).noneMatch(a -> a.startsWith("offer_"));
    }

    @Test
    void unverifiedDuplicateAsksInsteadOfResolving() {   // F5
        var f = LlmClassifierService.fromGold(store.tickets.get("T32"));
        var c = contexts.build(store.tickets.get("T03").customerId);   // no duplicate charge
        PolicyDecision d = policy.decide(f, c, 1);
        assertThat(d.actions()).containsExactly("ask_clarification");
        assertThat(d.mode()).isEqualTo("suggest");
    }
}
