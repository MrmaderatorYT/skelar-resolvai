package com.skelar.defuse.service.analytics;

import com.skelar.defuse.model.domain.HistoryTicket;
import com.skelar.defuse.model.dto.TicketAnalysis;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Dashboard numbers: this session's results vs the synthetic human-only history month. */
@Service
public class AnalyticsService {
    public static final Map<String, String> FIXES = Map.of(
            "charged_after_trial", "Email + push 24h before the trial ends, with a one-tap cancel link",
            "cant_find_cancel", "Put 'Cancel subscription' in Settings > Membership, 2 taps max",
            "price_surprise_renewal", "Show the renewal price and date on the paywall and in the receipt",
            "duplicate_charge", "Idempotency keys on the checkout retry",
            "technical_bug", "Crash-free sessions alert on releases; restore streaks from server",
            "account_access", "Magic-link login and self-serve email change",
            "content_question", "Add answers to the in-app FAQ",
            "cross_rail_cancellation_mismatch", "Show which rail bills the customer (App Store vs web card) in Settings and on every receipt, with a cancel link per rail");

    /** Reason prefix (text before ':') -> review reason code (spec K). Order matters: first match wins. */
    static final List<Map.Entry<String, String>> REVIEW_CODES = List.of(
            Map.entry("risk_not_calibrated", "RISK_NOT_CALIBRATED"),
            Map.entry("above_auto_refund_limit", "AMOUNT_OVER_LIMIT"),
            Map.entry("low_confidence", "LOW_CONFIDENCE"),
            Map.entry("refund_or_chargeback_history", "REFUND_HISTORY"),
            Map.entry("dispute_already_open", "DISPUTE_OPEN"),
            Map.entry("prompt_injection", "INJECTION"),
            Map.entry("injection caught by regex guard", "INJECTION"),
            Map.entry("identity_verification_required", "IDENTITY_UNCERTAIN"),
            Map.entry("identity", "IDENTITY_UNCERTAIN"),
            Map.entry("store_billing", "PROVIDER_UNSUPPORTED"),
            Map.entry("store_pause_requires_store_workflow", "PROVIDER_UNSUPPORTED"),
            Map.entry("unknown_billing_processor", "PROVIDER_UNSUPPORTED"),
            Map.entry("cross_rail", "CROSS_RAIL"),
            Map.entry("payment_not_refundable", "PAYMENT_NOT_REFUNDABLE"),
            Map.entry("already_refunded", "ALREADY_REFUNDED"),
            Map.entry("ambiguous_subscriptions", "AMBIGUOUS_SUBSCRIPTION"),
            Map.entry("provider_timeout", "PROVIDER_TIMEOUT"),
            Map.entry("autonomy_gate_assist", "GATE_ASSIST"),
            Map.entry("autonomy_gate_human", "GATE_HUMAN"));

    public static String reviewCode(String reason) {
        String prefix = reason.contains(":") ? reason.substring(0, reason.indexOf(':')).trim() : reason.trim();
        return REVIEW_CODES.stream().filter(e -> prefix.startsWith(e.getKey())).map(Map.Entry::getValue).findFirst().orElse("OTHER");
    }

    private final InMemoryStore store;
    private final AuditService audit;
    private final MoneyService money;
    private final com.skelar.defuse.data.AnalyticsRepository repository;

    private final int retentionPercent;
    private final int retentionMonths;
    private final Map<String, Integer> periodDays;

    public AnalyticsService(InMemoryStore store, AuditService audit, MoneyService money,
                            com.skelar.defuse.service.policy.ReferencePolicyService policy,
                            com.skelar.defuse.data.AnalyticsRepository repository) {
        this.repository = repository;
        this.retentionPercent = policy.retentionDiscountPercent();
        this.retentionMonths = policy.retentionDiscountMonths();
        this.periodDays = policy.properties().refund().planPeriodDays();
        this.store = store;
        this.audit = audit;
        this.money = money;
    }

    private static double r2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    private static double median(List<Double> xs) {
        if (xs.isEmpty()) return 0;
        List<Double> s = xs.stream().sorted().toList();
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2;
    }

    public Map<String, Object> summary() {
        List<TicketAnalysis> as = new ArrayList<>(store.analyses.values());
        int n = as.size();
        Map<String, Long> byStatus = as.stream().collect(Collectors.groupingBy(a -> a.status, TreeMap::new, Collectors.counting()));
        List<Map<String, Object>> events = audit.all();
        var humanTickets = events.stream().filter(e -> "human_reply".equals(e.get("event")) || "approved".equals(e.get("event")))
                .map(e -> (String) e.get("ticket_id")).collect(Collectors.toSet());
        long auto = as.stream().filter(a -> "resolved".equals(a.status) && a.decision.mode().equals("auto") && !humanTickets.contains(a.ticketId)).count();
        double refunded = audit.all().stream().filter(e -> "executed".equals(e.get("event")))
                .mapToDouble(e -> ((Number) e.getOrDefault("amount", 0)).doubleValue()).sum();
        List<TicketAnalysis> resolved = as.stream().filter(a -> a.status.equals("resolved")).toList();
        List<HistoryTicket> hist = store.history;

        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("label", "Synthetic month of human-only support");
        baseline.put("tickets", hist.size());
        baseline.put("median_first_response_hours", Math.round(median(hist.stream().map(t -> t.firstResponseHours).toList()) * 10) / 10.0);
        baseline.put("median_resolution_hours", Math.round(median(hist.stream().map(t -> t.resolutionHours).toList()) * 10) / 10.0);
        baseline.put("chargebacks_filed", hist.stream().filter(t -> t.chargebackFiledAfter).count());
        baseline.put("p_dispute_by_band", money.pDisputeByBand());

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("tickets_total", store.tickets.size());
        s.put("analyzed", n);
        s.put("by_status", byStatus);
        s.put("auto_resolved_pct", n == 0 ? 0.0 : Math.round(1000.0 * auto / n) / 10.0);
        s.put("refunded_usd", r2(refunded));
        s.put("gross_exposure_usd", r2(as.stream().filter(a -> a.money != null && a.decision.riskBand().equals("high"))
                .mapToDouble(a -> a.money.amountAtRisk() + a.money.feesAtRisk()).sum()));
        // illustrative scenarios from the synthetic baseline, never claimed as avoided or saved (spec K)
        s.put("dispute_fee_scenario_usd", r2(resolved.stream().filter(a -> a.money != null).mapToDouble(a -> a.money.disputeFeeScenario()).sum()));
        s.put("dispute_scenario_count", r2(resolved.stream().filter(a -> a.money != null && a.money.amountAtRisk() > 0)
                .mapToDouble(a -> a.money.pDispute()).sum()));
        s.put("high_risk_tickets", as.stream().filter(a -> a.decision.riskBand().equals("high")).count());
        s.put("sarcasm_detected", as.stream().filter(a -> a.features.sarcasm()).count());
        s.put("injections_blocked", as.stream().filter(a -> a.features.injection()).count());
        s.put("ai_median_latency_ms", n == 0 ? null : median(as.stream().map(a -> (double) a.latencyMs).toList()));
        Map<String, Object> retention = new LinkedHashMap<>();
        retention.put("offers_sent", as.stream().filter(a -> a.decision.actions().stream().anyMatch(x -> x.startsWith("offer_"))).count());
        retention.put("offers_accepted", events.stream().filter(e -> "offer_accepted".equals(e.get("event"))).count());
        retention.put("offers_declined", events.stream().filter(e -> "offer_declined".equals(e.get("event"))).count());
        retention.put("not_eligible", as.stream().flatMap(a -> a.decision.reasons().stream())
                .filter(r -> r.startsWith("retention_not_eligible: "))
                .collect(Collectors.groupingBy(r -> r.substring("retention_not_eligible: ".length()), TreeMap::new, Collectors.counting())));
        long accepted = (long) retention.get("offers_accepted");
        long sent = (long) retention.get("offers_sent");
        // a discount is a cost: accepted discounts x monthly price x percent x months
        double discountCost = events.stream().filter(e -> "offer_accepted".equals(e.get("event")) && "apply_discount".equals(e.get("action")))
                .mapToDouble(e -> {
                    var t = store.tickets.get((String) e.get("ticket_id"));
                    if (t == null) return 0;
                    var sub = store.subscription(t.customerId);
                    int period = periodDays.getOrDefault(sub.plan, 30);
                    return sub.price * 30.0 / period * retentionPercent / 100.0 * retentionMonths;   // monthly-equivalent price
                }).sum();
        retention.put("acceptance_rate", sent == 0 ? null : Math.round(1000.0 * accepted / sent) / 10.0);
        retention.put("holdout_customers", as.stream().filter(a -> a.decision.reasons().contains("retention_not_eligible: retention_holdout")).count());
        retention.put("discount_cost_usd", r2(discountCost));
        s.put("retention", retention);
        var m = money.settings();
        Map<String, Object> fees = new LinkedHashMap<>();
        fees.put("dispute_fee_usd", m.disputeFeeUsd());
        fees.put("scheme_fee_per_dispute_usd", m.schemeFeePerDisputeUsd());
        fees.put("merchant_region", m.merchantRegion());
        fees.put("vamp_threshold", m.vampThreshold());
        fees.put("note", "A refund is a cost, not a saving. Dispute-fee figures are scenarios from synthetic data, not measured outcomes.");
        s.put("money_settings", fees);
        s.put("baseline", baseline);
        return s;
    }

    /** Current demo outcomes and historical synthetic segments; no causal savings claims. */
    public Map<String, Object> diagnostics() {
        synchronized (store) {
            var events = audit.all();
            var human = events.stream().filter(e -> "human_reply".equals(e.get("event"))).map(e -> (String) e.get("ticket_id")).collect(Collectors.toSet());
            var approved = events.stream().filter(e -> "approved".equals(e.get("event"))).map(e -> (String) e.get("ticket_id")).collect(Collectors.toSet());
            long humanOnly = 0, assisted = 0, automatic = 0;
            for (var a : store.analyses.values()) {
                if (!"resolved".equals(a.status)) continue;
                if (human.contains(a.ticketId)) humanOnly++;
                else if (approved.contains(a.ticketId)) assisted++;
                else if ("auto".equals(a.decision.mode())) automatic++;
            }
            Map<String, Long> reasons = store.analyses.values().stream().flatMap(a -> a.decision.reasons().stream())
                    .collect(Collectors.groupingBy(x -> x, TreeMap::new, Collectors.counting()));
            Map<String, Long> segments = new TreeMap<>();
            for (var h : store.history) {
                try {
                    var customer = store.customer(h.customerId); var subscription = store.subscription(h.customerId);
                    String channel = customer.acquisitionChannel == null ? "unknown" : customer.acquisitionChannel;
                    String processor = subscription.processor == null ? "unknown" : subscription.processor;
                    String cause = h.rootCause == null ? "unknown" : h.rootCause;
                    segments.merge(channel + " | " + cause + " | " + processor, 1L, Long::sum);
                } catch (java.util.NoSuchElementException ignored) { /* Missing join is not attributed to a segment. */ }
            }
            var topSegments = segments.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                    .limit(20).map(e -> Map.of("segment", e.getKey(), "count", e.getValue())).toList();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("resolved", Map.of("auto", automatic, "assisted", assisted, "human", humanOnly));
            out.put("reasons", reasons);
            out.put("synthetic_segments", topSegments);
            out.put("sql_segments", repository.segments());   // SQL data layer (P3), kept beside the in-memory segments
            out.put("rail_mix", repository.railMix());
            out.put("multi_rail_customers", repository.multiRailCustomers());
            out.put("root_causes", rootCauses().stream().limit(5).toList());
            out.put("routes", routes());
            out.put("review_reasons", reviewReasons());
            out.put("blocked_money_actions", blockedMoneyActions());
            out.put("risk_status", "UNVALIDATED");   // no calibrated per-action error bound exists yet (spec E1)
            return out;
        }
    }

    /** Display routes: auto -> AUTO, suggest -> REVIEW, escalate -> INVESTIGATE. */
    Map<String, Long> routes() {
        Map<String, Long> out = new LinkedHashMap<>(Map.of("auto", 0L));
        out.put("review", 0L);
        out.put("investigate", 0L);
        for (var a : store.analyses.values()) {
            String key = switch (a.decision.mode()) { case "auto" -> "auto"; case "suggest" -> "review"; default -> "investigate"; };
            out.merge(key, 1L, Long::sum);
        }
        return out;
    }

    /** Why a ticket did not run on its own: one count per code per non-auto analysis. Retention notes are not review reasons. */
    Map<String, Long> reviewReasons() {
        Map<String, Long> out = new TreeMap<>();
        for (var a : store.analyses.values()) {
            if ("auto".equals(a.decision.mode())) continue;
            var codes = a.decision.reasons().stream().filter(r -> !r.startsWith("retention_"))
                    .map(AnalyticsService::reviewCode).collect(Collectors.toCollection(java.util.TreeSet::new));
            if (codes.size() > 1) codes.remove("OTHER");
            if (codes.isEmpty()) codes.add("OTHER");
            codes.forEach(c -> out.merge(c, 1L, Long::sum));
        }
        return out;
    }

    /** Money the customer asked for or the policy proposed that the machine did not move: waiting for review,
     *  blocked by a hard gate, refused or unconfirmed by the provider. */
    long blockedMoneyActions() {
        long n = 0;
        for (var a : store.analyses.values()) {
            if ("auto".equals(a.decision.mode())) continue;
            boolean asked = a.features != null && com.skelar.defuse.service.policy.ReferencePolicyService.MONEY_INTENTS.contains(a.features.intent());
            List<String> proposed = a.decision.actions().stream().filter(x -> x.startsWith("refund_")).toList();
            if (!asked && proposed.isEmpty()) continue;
            boolean moved = proposed.stream().map(x -> store.executed.get(a.ticketId + ":" + x))
                    .anyMatch(r -> r != null && !r.containsKey("error") && !Boolean.TRUE.equals(r.get("pending")));
            if (!moved) n++;
        }
        return n;
    }

    public List<Map<String, Object>> rootCauses() {
        List<HistoryTicket> hist = store.history;
        Map<String, List<HistoryTicket>> groups = hist.stream().collect(Collectors.groupingBy(t -> t.rootCause));
        return groups.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<HistoryTicket>> e) -> -e.getValue().size()))
                .map(e -> {
                    List<HistoryTicket> g = e.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("root_cause", e.getKey());
                    row.put("count", g.size());
                    row.put("share", Math.round(1000.0 * g.size() / hist.size()) / 1000.0);
                    row.put("avg_risk", r2(g.stream().mapToDouble(t -> t.riskScore).average().orElse(0)));
                    row.put("chargeback_rate", Math.round(1000.0 * g.stream().filter(t -> t.chargebackFiledAfter).count() / g.size()) / 1000.0);
                    row.put("recommended_fix", FIXES.getOrDefault(e.getKey(), ""));
                    return row;
                }).toList();
    }
}
