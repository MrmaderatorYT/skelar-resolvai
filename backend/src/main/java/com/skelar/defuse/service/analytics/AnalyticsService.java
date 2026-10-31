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
    static final Map<String, String> FIXES = Map.of(
            "charged_after_trial", "Email + push 24h before the trial ends, with a one-tap cancel link",
            "cant_find_cancel", "Put 'Cancel subscription' in Settings > Membership, 2 taps max",
            "price_surprise_renewal", "Show the renewal price and date on the paywall and in the receipt",
            "duplicate_charge", "Idempotency keys on the checkout retry",
            "technical_bug", "Crash-free sessions alert on releases; restore streaks from server",
            "account_access", "Magic-link login and self-serve email change",
            "content_question", "Add answers to the in-app FAQ");

    private final InMemoryStore store;
    private final AuditService audit;
    private final MoneyService money;

    public AnalyticsService(InMemoryStore store, AuditService audit, MoneyService money) {
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
        long auto = as.stream().filter(a -> a.decision.mode().equals("auto")).count();
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
        s.put("gross_exposure_usd", r2(as.stream().filter(a -> a.decision.riskBand().equals("high"))
                .mapToDouble(a -> a.money.amountAtRisk() + a.money.feesAtRisk()).sum()));
        s.put("expected_fees_avoided_usd", r2(resolved.stream().mapToDouble(a -> a.money.expectedFeesAvoided()).sum()));
        s.put("expected_chargebacks_avoided", r2(resolved.stream().filter(a -> a.money.amountAtRisk() > 0)
                .mapToDouble(a -> a.money.pDispute()).sum()));
        s.put("high_risk_tickets", as.stream().filter(a -> a.decision.riskBand().equals("high")).count());
        s.put("sarcasm_detected", as.stream().filter(a -> a.features.sarcasm()).count());
        s.put("injections_blocked", as.stream().filter(a -> a.features.injection()).count());
        s.put("ai_median_latency_ms", n == 0 ? null : median(as.stream().map(a -> (double) a.latencyMs).toList()));
        s.put("baseline", baseline);
        return s;
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
