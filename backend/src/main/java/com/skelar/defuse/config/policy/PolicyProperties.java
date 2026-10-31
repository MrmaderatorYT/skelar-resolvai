package com.skelar.defuse.config.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every policy number, loaded from defuse-policy.yml. Each value must cite its evidence in `sources`
 * (docs/evidence/RULES.md). Invalid values fail at startup: the compact constructors throw.
 */
@ConfigurationProperties(prefix = "defuse.policy")
public record PolicyProperties(Refund refund, Retention retention, Risk risk, Money money, Gate gate, Map<String, String> sources) {

    /** Keys that must have an evidence entry in `sources`. */
    public static final List<String> REQUIRED_SOURCES = List.of(
            "refund-full-max-days", "refund-full-max-sessions", "refund-partial", "refund-partial-max-days",
            "refund-auto-limit-usd", "refund-goodwill-ltv-usd", "refund-min-auto-confidence",
            "retention-discount", "retention-pause", "retention-offers", "retention-cooldown-days",
            "retention-min-tenure", "retention-max-offers-per-30d", "retention-holdout",
            "autonomy-gate", "risk-weights", "money-dispute-fee", "money-scheme-fee", "money-vamp-threshold");

    public PolicyProperties {
        require(refund != null && retention != null && risk != null && money != null && gate != null, "all policy sections are required");
        require(sources != null, "sources are required");
        for (String key : REQUIRED_SOURCES) {
            require(sources.get(key) != null && !sources.get(key).isBlank(), "missing evidence source for " + key);
        }
    }

    public record Refund(int fullMaxDays, int fullMaxSessions, int partialMaxDays, Map<String, Integer> planPeriodDays,
                         double autoLimitUsd, double goodwillLtvUsd, double minAutoConfidence) {
        public Refund {
            require(fullMaxDays >= 0 && fullMaxSessions >= 0, "refund windows must be >= 0");
            require(partialMaxDays >= fullMaxDays, "partial-max-days must be >= full-max-days");
            require(planPeriodDays != null && !planPeriodDays.isEmpty()
                    && planPeriodDays.values().stream().allMatch(d -> d != null && d > 0), "plan-period-days must be > 0");
            require(finite(autoLimitUsd) && autoLimitUsd >= 0, "auto-limit-usd must be >= 0");
            require(finite(goodwillLtvUsd) && goodwillLtvUsd >= 0, "goodwill-ltv-usd must be >= 0");
            require(share(minAutoConfidence), "min-auto-confidence must be in 0..1");
        }

        /** Period of a plan, or -1 if the plan is not configured (callers must fail closed). */
        public int periodDays(String plan) {
            Integer d = plan == null ? null : planPeriodDays.get(plan);
            return d == null ? -1 : d;
        }
    }

    public record Retention(Map<String, String> offers, int discountPercent, int discountMonths, int pauseMonths,
                            int minTenureMonths, int minPaidRenewals, int cooldownDays, int maxOffersPer30d,
                            int holdoutBucket) {
        public Retention {
            require(offers != null && offers.values().stream().allMatch(Set.of("offer_discount", "offer_pause")::contains),
                    "offers must map reasons to offer_discount or offer_pause");
            require(discountPercent >= 1 && discountPercent <= 90, "discount-percent must be in 1..90");
            require(discountMonths >= 1 && discountMonths <= 12, "discount-months must be in 1..12");
            require(pauseMonths >= 1 && pauseMonths <= 6, "pause-months must be in 1..6");
            require(minTenureMonths >= 0 && minPaidRenewals >= 0 && cooldownDays >= 0 && maxOffersPer30d >= 0,
                    "retention limits must be >= 0");
            require(holdoutBucket >= -1 && holdoutBucket <= 9, "holdout-bucket must be -1 (off) or 0..9");
        }
    }

    public record Risk(double base, double dispute, double sarcasm, double aggression, double money, double perContact,
                       int maxContacts, double bandHigh, double bandMedium) {
        public Risk {
            for (double w : new double[] {base, dispute, sarcasm, aggression, money, perContact, bandHigh, bandMedium}) {
                require(share(w), "risk weights and bands must be in 0..1");
            }
            require(maxContacts >= 0, "max-contacts must be >= 0");
            require(bandHigh > bandMedium, "band-high must be > band-medium");
        }
    }

    /** Autonomy gate. Priors are hand-set heuristics, not measured error rates; the confidence values are
     *  abstention thresholds on the model's self-score, not probabilities. riskStatus says whether an external
     *  calibration exists: UNVALIDATED refunds run on their own only in the mock-billing sandbox (spec E1, E4). */
    public record Gate(double autoErrorMax, double assistErrorMax, double autoImpactMaxUsd,
                       double assistImpactMaxUsd, double autoExpectedLossMaxUsd, Map<String, Double> priors,
                       double minAutoConfidence, double minAssistConfidence, String riskStatus,
                       boolean autoRefundEnabled, boolean sandbox) {
        public static final Set<String> RISK_STATUSES = Set.of("UNVALIDATED", "VALIDATED");

        public Gate {
            require(share(minAutoConfidence) && share(minAssistConfidence) && minAssistConfidence <= minAutoConfidence,
                    "gate confidence thresholds must be in 0..1 and min-assist <= min-auto");
            require(RISK_STATUSES.contains(riskStatus), "gate.risk-status must be UNVALIDATED or VALIDATED");
            require(share(autoErrorMax) && share(assistErrorMax) && autoErrorMax <= assistErrorMax, "invalid gate error thresholds");
            require(finite(autoImpactMaxUsd) && autoImpactMaxUsd >= 0 && finite(assistImpactMaxUsd) && assistImpactMaxUsd >= autoImpactMaxUsd, "invalid gate impact thresholds");
            require(finite(autoExpectedLossMaxUsd) && autoExpectedLossMaxUsd >= 0, "invalid gate expected loss threshold");
            require(priors != null && priors.keySet().containsAll(Set.of("question", "billing_question", "cancel", "cancel_refund", "refund", "duplicate_charge", "pause", "technical_issue", "account_access", "account_security", "unclear")) && priors.values().stream().allMatch(PolicyProperties::share), "invalid gate priors");
        }
    }

    public record Money(double disputeFeeUsd, double schemeFeePerDisputeUsd, String merchantRegion,
                        Map<String, Double> vampThresholdByRegion) {
        public Money {
            require(finite(disputeFeeUsd) && disputeFeeUsd >= 0 && finite(schemeFeePerDisputeUsd) && schemeFeePerDisputeUsd >= 0,
                    "fees must be >= 0");
            require(vampThresholdByRegion != null && merchantRegion != null && vampThresholdByRegion.containsKey(merchantRegion),
                    "merchant-region must be one of vamp-threshold-by-region");
            require(vampThresholdByRegion.values().stream().allMatch(PolicyProperties::share), "VAMP thresholds must be in 0..1");
        }

        public double feesPerDispute() {
            return disputeFeeUsd + schemeFeePerDisputeUsd;
        }

        public double vampThreshold() {
            return vampThresholdByRegion.get(merchantRegion);
        }
    }

    /** Same values as defuse-policy.yml; for tests and tools that run without Spring. */
    public static PolicyProperties defaults() {
        Map<String, String> sources = new LinkedHashMap<>();
        REQUIRED_SOURCES.forEach(k -> sources.put(k, "RULES.md"));
        return new PolicyProperties(
                new Refund(14, 2, 30, Map.of("monthly", 30, "quarterly", 91, "annual", 365), 50, 100, 0.85),
                new Retention(Map.of("too_expensive", "offer_discount", "no_time", "offer_pause", "not_using", "offer_pause"),
                        30, 3, 2, 2, 1, 90, 50, 3),
                new Risk(0.10, 0.40, 0.25, 0.20, 0.15, 0.10, 3, 0.50, 0.30),
                new Money(15, 0, "EU", Map.of("US", 0.015, "CA", 0.015, "EU", 0.015, "AP", 0.015, "CEMEA", 0.022)),
                new Gate(.03, .15, 50, 500, 1.50, Map.ofEntries(Map.entry("question", .01), Map.entry("billing_question", .02), Map.entry("cancel", .01), Map.entry("cancel_refund", .02), Map.entry("refund", .02), Map.entry("duplicate_charge", .02), Map.entry("pause", .02), Map.entry("technical_issue", .05), Map.entry("account_access", .10), Map.entry("account_security", .10), Map.entry("unclear", .50)),
                        .97, .85, "UNVALIDATED", true, true),
                sources);
    }

    /** Flat view for the Policy tab and /api/health: key, value, evidence. */
    public List<Map<String, Object>> rules() {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        java.util.function.BiConsumer<String, Object[]> add = (src, kv) -> {
            for (int i = 0; i < kv.length; i += 2) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", kv[i]);
                row.put("value", kv[i + 1]);
                row.put("source", sources.get(src));
                out.add(row);
            }
        };
        add.accept("refund-full-max-days", new Object[] {"refund.full-max-days", refund.fullMaxDays()});
        add.accept("refund-full-max-sessions", new Object[] {"refund.full-max-sessions", refund.fullMaxSessions()});
        add.accept("refund-partial", new Object[] {"refund.partial", "pro-rata unused days"});
        add.accept("refund-partial-max-days", new Object[] {"refund.partial-max-days", refund.partialMaxDays()});
        add.accept("refund-auto-limit-usd", new Object[] {"refund.auto-limit-usd", refund.autoLimitUsd()});
        add.accept("refund-goodwill-ltv-usd", new Object[] {"refund.goodwill-ltv-usd", refund.goodwillLtvUsd()});
        add.accept("refund-min-auto-confidence", new Object[] {"refund.min-auto-confidence", refund.minAutoConfidence()});
        add.accept("retention-discount", new Object[] {"retention.discount-percent", retention.discountPercent(),
                "retention.discount-months", retention.discountMonths()});
        add.accept("retention-pause", new Object[] {"retention.pause-months", retention.pauseMonths()});
        add.accept("retention-offers", new Object[] {"retention.offers", retention.offers()});
        add.accept("retention-cooldown-days", new Object[] {"retention.cooldown-days", retention.cooldownDays()});
        add.accept("retention-min-tenure", new Object[] {"retention.min-tenure-months", retention.minTenureMonths(),
                "retention.min-paid-renewals", retention.minPaidRenewals()});
        add.accept("retention-max-offers-per-30d", new Object[] {"retention.max-offers-per-30d", retention.maxOffersPer30d()});
        add.accept("retention-holdout", new Object[] {"retention.holdout-bucket", retention.holdoutBucket()});
        add.accept("autonomy-gate", new Object[] {"gate.auto-error-max", gate.autoErrorMax(), "gate.assist-error-max", gate.assistErrorMax(), "gate.auto-impact-max-usd", gate.autoImpactMaxUsd(), "gate.assist-impact-max-usd", gate.assistImpactMaxUsd(), "gate.auto-expected-loss-max-usd", gate.autoExpectedLossMaxUsd(),
                "gate.min-auto-confidence", gate.minAutoConfidence(), "gate.min-assist-confidence", gate.minAssistConfidence(),
                "gate.risk-status", gate.riskStatus(), "gate.auto-refund-enabled", gate.autoRefundEnabled(), "gate.sandbox", gate.sandbox()});
        add.accept("risk-weights", new Object[] {"risk.weights", Map.of("dispute", risk.dispute(), "sarcasm", risk.sarcasm(),
                "aggression", risk.aggression(), "money", risk.money(), "per-contact", risk.perContact()),
                "risk.bands", Map.of("high", risk.bandHigh(), "medium", risk.bandMedium())});
        add.accept("money-dispute-fee", new Object[] {"money.dispute-fee-usd", money.disputeFeeUsd()});
        add.accept("money-scheme-fee", new Object[] {"money.scheme-fee-per-dispute-usd", money.schemeFeePerDisputeUsd()});
        add.accept("money-vamp-threshold", new Object[] {"money.vamp-threshold", money.vampThreshold(),
                "money.merchant-region", money.merchantRegion()});
        return out;
    }

    static boolean finite(double x) {
        return Double.isFinite(x);
    }

    static boolean share(double x) {
        return Double.isFinite(x) && x >= 0 && x <= 1;
    }

    static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException("Invalid defuse-policy.yml: " + message);
    }
}
