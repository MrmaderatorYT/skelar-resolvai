package com.skelar.defuse.service.policy;

import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.LastPayment;
import com.skelar.defuse.model.dto.PolicyDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Deterministic policy, a 1:1 port of defuse-data/reference_policy.py.
 * The LLM extracts features; only this class decides actions, amounts and the execution mode.
 */
@Service
public class ReferencePolicyService {
    public static final double AUTO_REFUND_LIMIT_USD = 60.0;
    public static final int FULL_REFUND_MAX_DAYS = 14;
    public static final int FULL_REFUND_MAX_SESSIONS = 2;
    public static final int PARTIAL_REFUND_MAX_DAYS = 30;
    public static final double PARTIAL_REFUND_SHARE = 0.5;
    public static final double GOODWILL_LTV_USD = 100.0;
    public static final double MIN_AUTO_CONFIDENCE = 0.85;

    static final double W_BASE = 0.10, W_DISPUTE = 0.40, W_SARCASM = 0.25, W_AGGRESSION = 0.20,
            W_MONEY = 0.15, W_PER_CONTACT = 0.10;
    static final int MAX_CONTACTS = 3;
    static final double BAND_HIGH = 0.50, BAND_MEDIUM = 0.30;

    public static final Set<String> REFUND_INTENTS = Set.of("refund", "cancel_refund");
    public static final Set<String> CANCEL_INTENTS = Set.of("cancel", "cancel_refund");
    public static final Set<String> MONEY_INTENTS = Set.of("refund", "cancel_refund", "duplicate_charge");
    public static final Set<String> INTENTS = Set.of("cancel", "refund", "cancel_refund", "duplicate_charge",
            "pause", "technical_issue", "account_access", "account_security", "question", "billing_question", "unclear");

    // retention: one save offer per ticket, only for the right customer; everyone else gets an honest cancel
    public static final Map<String, String> RETENTION_OFFERS =
            Map.of("too_expensive", "offer_discount", "no_time", "offer_pause", "not_using", "offer_pause");
    public static final int DISCOUNT_PERCENT = 25, DISCOUNT_MONTHS = 2, PAUSE_MONTHS = 2;
    public static final int MIN_TENURE_MONTHS = 2, MIN_PAID_RENEWALS = 1, OFFER_COOLDOWN_DAYS = 365;
    public static final int MAX_OFFERS_PER_30D = 50;
    public static final int HOLDOUT_BUCKET = 3;   // customer id % 10 == 3 never gets offers: measures real uplift

    public record Risk(double score, String band) {
    }

    public Risk riskScore(ClassificationFeatures f, int contactsLast30d) {
        double s = W_BASE;
        if (f.disputeSignal()) s += W_DISPUTE;
        if (f.sarcasm()) s += W_SARCASM;
        if (f.hiddenAggression() || f.explicitAggression()) s += W_AGGRESSION;
        if (MONEY_INTENTS.contains(f.intent())) s += W_MONEY;
        s += Math.min(contactsLast30d, MAX_CONTACTS) * W_PER_CONTACT;
        s = Math.round(Math.min(s, 1.0) * 100.0) / 100.0;
        String band = s >= BAND_HIGH ? "high" : s >= BAND_MEDIUM ? "medium" : "low";
        return new Risk(s, band);
    }

    static double floorCents(double x) {
        return Math.floor(x * 100 + 1e-6) / 100;
    }

    static String priority(ClassificationFeatures f, String band, boolean disputed) {
        if (f.injection() || "account_security".equals(f.intent()) || band.equals("high") || disputed) return "P1";
        if (band.equals("medium") || "account_access".equals(f.intent())) return "P2";
        return Set.of("question", "billing_question", "unclear").contains(f.intent()) ? "P4" : "P3";
    }

    public PolicyDecision decide(ClassificationFeatures f, CustomerContext ctx, double confidence) {
        // Fail closed on anything the LLM could get wrong: unknown intent, NaN or out-of-range confidence.
        if (f == null || !INTENTS.contains(f.intent()) || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            return new PolicyDecision(List.of(), "escalate", 0, 0, "low", "P1",
                    new ArrayList<>(List.of("invalid_features_or_confidence")));
        }
        String intent = f.intent();
        ClassificationFeatures feat = f;
        Risk risk = riskScore(feat, ctx.contactsLast30d());
        LastPayment lp = ctx.lastPayment();
        boolean disputed = "disputed".equals(lp.status());
        List<String> reasons = new ArrayList<>();
        Out out = new Out(feat, risk, disputed, confidence, reasons, ctx);

        if (feat.injection()) {
            reasons.add("prompt_injection: no actions, human review");
            return out.of(List.of(), "escalate", 0);
        }
        if (disputed) {
            reasons.add("dispute_already_open: refunding now would pay twice");
            return out.of(List.of("evidence_pack"), "escalate", 0);
        }
        if (intent.equals("account_access") || intent.equals("account_security")) {
            reasons.add("identity_verification_required");
            return out.of(List.of(), "escalate", 0);
        }
        if (intent.equals("unclear")) {
            return out.of(List.of("ask_clarification"), "auto", 0);
        }

        String processor = ctx.billingProcessor() != null ? ctx.billingProcessor()
                : switch (ctx.platform()) {
                    case "web" -> "card";
                    case "ios" -> "app_store";
                    case "android" -> "google_play";
                    default -> "unknown";
                };
        if (!Set.of("card", "app_store", "google_play").contains(processor)) {
            reasons.add("unknown_billing_processor");
            return out.of(List.of(), "escalate", 0);
        }
        if (!processor.equals("card")) {
            if (intent.equals("pause")) {
                reasons.add("store_pause_requires_store_workflow");
                return out.of(List.of(), "escalate", 0);
            }
            List<String> acts = new ArrayList<>();
            if (MONEY_INTENTS.contains(intent)) acts.add("store_refund_instructions");
            if (CANCEL_INTENTS.contains(intent)) acts.add("store_cancel_instructions");
            if (!acts.isEmpty()) {
                reasons.add("store_billing: only Apple/Google can refund or cancel");
                return out.of(acts, "auto", 0);
            }
        }

        if (intent.equals("cancel") && Set.of("active", "paused").contains(ctx.subscriptionStatus())) {
            String[] offer = retentionOffer(feat, ctx);
            if (offer[0] != null) {
                reasons.add("retention_offer: one offer; the customer can still cancel at any time");
                return out.of(List.of(offer[0]), "auto", 0);
            }
            if (offer[1] != null) reasons.add("retention_not_eligible: " + offer[1]);
        }

        List<String> acts = new ArrayList<>();
        double refund = 0;
        String mode = "auto";
        if (CANCEL_INTENTS.contains(intent) && Set.of("active", "paused").contains(ctx.subscriptionStatus())) {
            acts.add("cancel");
        }
        if (intent.equals("duplicate_charge")) {
            if (ctx.duplicatePaymentId() == null) {
                reasons.add("duplicate_not_verified");
                return out.of(List.of("ask_clarification"), "suggest", 0);
            }
            acts.add("refund_duplicate");
            refund = lp.amount();
        } else if (REFUND_INTENTS.contains(intent)) {
            long days = lp.daysAgo();
            int sessions = ctx.sessionsSinceLastCharge();
            if (days <= FULL_REFUND_MAX_DAYS && sessions <= FULL_REFUND_MAX_SESSIONS) {
                acts.add("refund_full");
                refund = lp.amount();
            } else if (days <= PARTIAL_REFUND_MAX_DAYS) {
                acts.add("refund_partial");
                refund = floorCents(lp.amount() * PARTIAL_REFUND_SHARE);
            } else if (risk.band().equals("high")) {
                acts.add("refund_partial_goodwill");
                refund = floorCents(lp.amount() * PARTIAL_REFUND_SHARE);
                mode = "suggest";
                reasons.add("outside_window_but_high_dispute_risk: goodwill needs a human");
            } else {
                reasons.add("outside_refund_window");
            }
        } else if (intent.equals("pause")) {
            acts.add("pause");
        } else if (intent.equals("technical_issue")
                && (ctx.ltvUsd() >= GOODWILL_LTV_USD || !risk.band().equals("low"))) {
            acts.add("extend_access_7d");
            mode = "suggest";
        }
        return out.of(acts, mode, refund);
    }

    /** {offerAction, null} if a save offer is allowed; {null, whyNot} if not; {null, null} if the
     *  cancel reason is not one we make offers for. Deterministic: the LLM only names the reason. */
    public String[] retentionOffer(ClassificationFeatures f, CustomerContext ctx) {
        String offer = f.cancelReason() == null ? null : RETENTION_OFFERS.get(f.cancelReason());
        if (offer == null) return new String[] {null, null};
        String why = null;
        if (f.disputeSignal() || f.hiddenAggression() || f.explicitAggression()) why = "dispute_or_aggression";
        else if (ctx.contactsLast30d() > 0) why = "repeat_contact";
        else if (ctx.tenureMonths() < MIN_TENURE_MONTHS || ctx.paidRenewals() < MIN_PAID_RENEWALS) why = "tenure";
        else if (ctx.offers12m() > 0) why = "offer_cooldown";
        else if (ctx.identityOffers12m() > 0) why = "identity_cluster_offer";
        else if (ctx.refundsLast180d() > 0 || ctx.chargebacksLifetime() > 0) why = "refund_or_chargeback_history";
        else if (ctx.retentionOffers30d() >= MAX_OFFERS_PER_30D) why = "monthly_offer_budget";
        else if (ctx.inHoldout()) why = "retention_holdout";
        return why == null ? new String[] {offer, null} : new String[] {null, why};
    }

    private record Out(ClassificationFeatures f, Risk risk, boolean disputed, double confidence, List<String> reasons,
                       CustomerContext ctx) {
        /** Every money path (incl. duplicates) goes through the same gate. */
        PolicyDecision of(List<String> actions, String mode, double refund) {
            if (refund > 0) {
                LastPayment lp = ctx.lastPayment();
                if (!"succeeded".equals(lp.status()) || lp.daysAgo() < 0) {
                    refund = 0;
                    actions = actions.stream().filter(a -> !a.startsWith("refund_")).toList();
                    mode = actions.isEmpty() ? "escalate" : "suggest";
                    reasons.add("payment_not_refundable");
                } else if (refund > AUTO_REFUND_LIMIT_USD) {
                    mode = "suggest";
                    reasons.add("above_auto_refund_limit");
                }
                if (refund > 0 && (ctx.refundsLast180d() > 0 || ctx.chargebacksLifetime() > 0)) {
                    mode = "suggest";
                    reasons.add("refund_or_chargeback_history");
                }
            }
            if (mode.equals("auto") && confidence < MIN_AUTO_CONFIDENCE) {
                mode = "suggest";
                reasons.add("low_confidence");
            }
            List<String> acts = new ArrayList<>(actions);
            if (acts.isEmpty() && !mode.equals("escalate")) acts.add("reply_only");
            acts.sort(null);
            return new PolicyDecision(List.copyOf(acts), mode, Math.round(refund * 100.0) / 100.0,
                    risk.score(), risk.band(), priority(f, risk.band(), disputed), reasons);
        }
    }
}
