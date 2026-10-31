package com.skelar.defuse.service.policy;

import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.LastPayment;
import com.skelar.defuse.model.dto.PolicyDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.skelar.defuse.config.policy.PolicyProperties;

/**
 * Deterministic policy, a 1:1 port of defuse-data/reference_policy.py.
 * The LLM extracts features; only this class decides actions, amounts and the execution mode.
 */
@Service
public class ReferencePolicyService {
    private final PolicyProperties props;
    private final PolicyProperties.Refund refundCfg;
    private final PolicyProperties.Retention retentionCfg;
    private final PolicyProperties.Risk riskCfg;

    public ReferencePolicyService(PolicyProperties props) {
        this.props = props;
        this.refundCfg = props.refund();
        this.retentionCfg = props.retention();
        this.riskCfg = props.risk();
    }

    public PolicyProperties properties() {
        return props;
    }

    public double autoRefundLimitUsd() {
        return refundCfg.autoLimitUsd();
    }

    public Map<String, Object> effectiveSettings(Map<String, Object> seed) {
        Map<String, Object> result = new LinkedHashMap<>(seed);
        props.rules().forEach(r -> result.put((String) r.get("key"), r.get("value")));
        return result;
    }

    public static final Set<String> REFUND_INTENTS = Set.of("refund", "cancel_refund");
    public static final Set<String> CANCEL_INTENTS = Set.of("cancel", "cancel_refund");
    public static final Set<String> MONEY_INTENTS = Set.of("refund", "cancel_refund", "duplicate_charge");
    public static final Set<String> INTENTS = Set.of("cancel", "refund", "cancel_refund", "duplicate_charge",
            "pause", "technical_issue", "account_access", "account_security", "question", "billing_question", "unclear");

    // retention: one save offer per ticket, only for the right customer; everyone else gets an honest cancel
    /** @deprecated read offer parameters through the getters below; these mirror defuse-policy.yml defaults
     *  (PolicyConfigTest keeps them equal) until executor/reply code switches to the getters. */
    @Deprecated
    public static final int DISCOUNT_PERCENT = 30, DISCOUNT_MONTHS = 3, PAUSE_MONTHS = 2;

    /** Offer parameters: the only way other components read them (frozen contract, config-driven later). */
    public int retentionDiscountPercent() {
        return retentionCfg.discountPercent();
    }

    public int retentionDiscountMonths() {
        return retentionCfg.discountMonths();
    }

    public int retentionPauseMonths() {
        return retentionCfg.pauseMonths();
    }

    public record Risk(double score, String band) {
    }

    public Risk riskScore(ClassificationFeatures f, int contactsLast30d) {
        PolicyProperties.Risk w = riskCfg;
        double s = w.base();
        if (f.disputeSignal()) s += w.dispute();
        if (f.sarcasm()) s += w.sarcasm();
        if (f.hiddenAggression() || f.explicitAggression()) s += w.aggression();
        if (MONEY_INTENTS.contains(f.intent())) s += w.money();
        s += Math.min(contactsLast30d, w.maxContacts()) * w.perContact();
        s = Math.round(Math.min(s, 1.0) * 100.0) / 100.0;
        String band = s >= w.bandHigh() ? "high" : s >= w.bandMedium() ? "medium" : "low";
        return new Risk(s, band);
    }

    static double floorCents(double x) {
        return Math.floor(x * 100 + 1e-6) / 100;
    }

    /** Refund for the unused part of the paid period (CRD art. 14(3), CJEU C-641/19), floored to cents. */
    static double proRata(double amount, long daysAgo, int periodDays) {
        if (periodDays <= 0) return 0;
        long unused = Math.max(periodDays - Math.max(daysAgo, 0), 0);
        return floorCents(amount * unused / periodDays);
    }

    static String priority(ClassificationFeatures f, String band, boolean disputed) {
        if (f.injection() || "account_security".equals(f.intent()) || band.equals("high") || disputed) return "P1";
        if (band.equals("medium") || "account_access".equals(f.intent())) return "P2";
        return Set.of("question", "billing_question", "unclear").contains(f.intent()) ? "P4" : "P3";
    }

    public record Autonomy(double pErr, double impactUsd, double expectedLossUsd, String level, String source) {}

    public Autonomy assess(ClassificationFeatures f, CustomerContext ctx, PolicyDecision decision, double confidence) {
        if (f == null || f.intent() == null || !Double.isFinite(confidence) || confidence < 0 || confidence > 1)
            return new Autonomy(1, 0, 0, "HUMAN", "invalid_features");
        var g = props.gate();
        double prior = g.priors().getOrDefault(f.intent(), 1.0);
        double pErr = Math.rint(Math.max(prior, 1 - confidence) * 1_000_000_000_000d) / 1_000_000_000_000d;
        int days = refundCfg.periodDays(ctx.plan());
        double impact = decision.refundAmount();
        if (decision.actions().contains("offer_discount") || decision.actions().contains("apply_discount")) {
            if (days <= 0) return new Autonomy(pErr, 0, 0, "HUMAN", "unknown_plan_period");
            impact += ctx.price() * 30.0 / days * retentionCfg.discountPercent() / 100.0 * retentionCfg.discountMonths();
        }
        if (decision.actions().contains("extend_access_7d")) {
            if (days <= 0) return new Autonomy(pErr, 0, 0, "HUMAN", "unknown_plan_period");
            impact += ctx.price() * 7.0 / days;
        }
        double expectedLoss = pErr * impact;
        boolean replyOnly = !decision.actions().isEmpty() && decision.actions().stream().allMatch(a -> Set.of("reply_only", "ask_clarification").contains(a));
        String level;
        if (f.injection() || "disputed".equals(ctx.lastPayment().status()) || Set.of("account_access", "account_security").contains(f.intent())) level = "HUMAN";
        else if (replyOnly) level = "AUTO";
        else if (pErr <= g.autoErrorMax() && impact <= g.autoImpactMaxUsd() && expectedLoss <= g.autoExpectedLossMaxUsd()) level = "AUTO";
        else if (pErr <= g.assistErrorMax() && impact <= g.assistImpactMaxUsd()) level = "ASSIST";
        else level = "HUMAN";
        return new Autonomy(pErr, impact, expectedLoss, level, "heuristic_prior");
    }
    public PolicyDecision decide(ClassificationFeatures f, CustomerContext ctx, double confidence) {
        PolicyDecision base = decideBase(f, ctx, confidence);
        Autonomy gate = assess(f, ctx, base, confidence);
        int policyRank = switch (base.mode()) { case "auto" -> 0; case "suggest" -> 1; default -> 2; };
        int gateRank = switch (gate.level()) { case "AUTO" -> 0; case "ASSIST" -> 1; default -> 2; };
        if (gateRank <= policyRank) return base;
        var reasons = new ArrayList<>(base.reasons());
        reasons.add("autonomy_gate_" + gate.level().toLowerCase(java.util.Locale.ROOT));
        return new PolicyDecision(base.actions(), gateRank == 1 ? "suggest" : "escalate", base.refundAmount(), base.riskScore(), base.riskBand(), base.priority(), reasons);
    }
    private PolicyDecision decideBase(ClassificationFeatures f, CustomerContext ctx, double confidence) {
        // Fail closed on anything the LLM could get wrong: unknown intent, NaN or out-of-range confidence.
        if (f == null || f.intent() == null || !INTENTS.contains(f.intent()) || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            return new PolicyDecision(List.of(), "escalate", 0, 0, "low", "P1",
                    new ArrayList<>(List.of("invalid_features_or_confidence")));
        }
        String intent = f.intent();
        ClassificationFeatures feat = f;
        Risk risk = riskScore(feat, ctx.contactsLast30d());
        LastPayment lp = ctx.lastPayment();
        boolean disputed = "disputed".equals(lp.status());
        List<String> reasons = new ArrayList<>();
        Out out = new Out(feat, risk, disputed, confidence, reasons, ctx, refundCfg.autoLimitUsd(), refundCfg.minAutoConfidence());

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
            if (days <= refundCfg.fullMaxDays() && sessions <= refundCfg.fullMaxSessions()) {
                acts.add("refund_full");
                refund = lp.amount();
            } else if (days <= refundCfg.partialMaxDays()) {
                acts.add("refund_partial");
                refund = proRata(lp.amount(), days, refundCfg.periodDays(ctx.plan()));
            } else if (risk.band().equals("high")) {
                acts.add("refund_partial_goodwill");
                refund = proRata(lp.amount(), days, refundCfg.periodDays(ctx.plan()));
                mode = "suggest";
                reasons.add("outside_window_but_high_dispute_risk: goodwill needs a human");
            } else {
                reasons.add("outside_refund_window");
            }
        } else if (intent.equals("pause")) {
            acts.add("pause");
        } else if (intent.equals("technical_issue")
                && (ctx.ltvUsd() >= refundCfg.goodwillLtvUsd() || !risk.band().equals("low"))) {
            acts.add("extend_access_7d");
            mode = "suggest";
        }
        boolean proRataRefund = acts.contains("refund_partial") || acts.contains("refund_partial_goodwill");
        if (proRataRefund && refundCfg.periodDays(ctx.plan()) < 0) {
            acts.removeIf(a -> a.startsWith("refund_"));
            refund = 0;
            mode = "suggest";
            reasons.add("unknown_plan_period: human decides the amount");
        } else if (proRataRefund && refund <= 0) {
            acts.removeIf(a -> a.startsWith("refund_"));
            refund = 0;
            reasons.add("nothing_left_to_refund: paid period already used");
        }
        return out.of(acts, mode, refund);
    }

    /** {offerAction, null} if a save offer is allowed; {null, whyNot} if not; {null, null} if the
     *  cancel reason is not one we make offers for. Deterministic: the LLM only names the reason. */
    public String[] retentionOffer(ClassificationFeatures f, CustomerContext ctx) {
        String offer = f.cancelReason() == null ? null : retentionCfg.offers().get(f.cancelReason());
        if (offer == null) return new String[] {null, null};
        String why = null;
        if (f.disputeSignal() || f.hiddenAggression() || f.explicitAggression()) why = "dispute_or_aggression";
        else if (ctx.contactsLast30d() > 0) why = "repeat_contact";
        else if (ctx.tenureMonths() < retentionCfg.minTenureMonths() || ctx.paidRenewals() < retentionCfg.minPaidRenewals()) why = "tenure";
        else if (ctx.offers12m() > 0) why = "offer_cooldown";
        else if (ctx.identityOffers12m() > 0) why = "identity_cluster_offer";
        else if (ctx.refundsLast180d() > 0 || ctx.chargebacksLifetime() > 0) why = "refund_or_chargeback_history";
        else if (ctx.retentionOffers30d() >= retentionCfg.maxOffersPer30d()) why = "monthly_offer_budget";
        else if (ctx.inHoldout()) why = "retention_holdout";
        return why == null ? new String[] {offer, null} : new String[] {null, why};
    }

    private record Out(ClassificationFeatures f, Risk risk, boolean disputed, double confidence, List<String> reasons,
                       CustomerContext ctx, double autoRefundLimitUsd, double minAutoConfidence) {
        /** Every money path (incl. duplicates) goes through the same gate. */
        PolicyDecision of(List<String> actions, String mode, double refund) {
            if (refund > 0) {
                LastPayment lp = ctx.lastPayment();
                if (!"succeeded".equals(lp.status()) || lp.daysAgo() < 0) {
                    refund = 0;
                    actions = actions.stream().filter(a -> !a.startsWith("refund_")).toList();
                    mode = actions.isEmpty() ? "escalate" : "suggest";
                    reasons.add("payment_not_refundable");
                } else if (refund > autoRefundLimitUsd) {
                    mode = "suggest";
                    reasons.add("above_auto_refund_limit");
                }
                if (refund > 0 && (ctx.refundsLast180d() > 0 || ctx.chargebacksLifetime() > 0)) {
                    mode = "suggest";
                    reasons.add("refund_or_chargeback_history");
                }
            }
            if (!mode.equals("escalate") && confidence < minAutoConfidence) {
                if (mode.equals("auto")) mode = "suggest";
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
