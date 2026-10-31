package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;

import com.skelar.defuse.service.analytics.AnalyticsService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Spec K: count routes and review reasons; money figures are scenarios, never "avoided" or "saved". */
@SpringBootTest
class AnalyticsContractTest {
    static final Set<String> CODES = Set.of("RISK_NOT_CALIBRATED", "AMOUNT_OVER_LIMIT", "LOW_CONFIDENCE", "REFUND_HISTORY",
            "DISPUTE_OPEN", "INJECTION", "IDENTITY_UNCERTAIN", "PROVIDER_UNSUPPORTED", "CROSS_RAIL", "PAYMENT_NOT_REFUNDABLE",
            "ALREADY_REFUNDED", "AMBIGUOUS_SUBSCRIPTION", "PROVIDER_TIMEOUT", "GATE_ASSIST", "GATE_HUMAN", "OTHER");

    @Autowired InMemoryStore store;
    @Autowired AuditService audit;
    @Autowired AnalyticsService analytics;
    @Autowired TicketPipelineService pipeline;

    @BeforeEach void analyzeAll() {
        store.reset(); audit.clear();
        new ArrayList<>(store.tickets.keySet()).forEach(id -> pipeline.analyze(id, false));
    }

    private static void keys(Object node, List<String> out) {
        if (node instanceof Map<?, ?> m) m.forEach((k, v) -> { out.add(String.valueOf(k)); keys(v, out); });
        if (node instanceof List<?> l) l.forEach(v -> keys(v, out));
    }

    @Test void summaryMakesNoAvoidedOrSavedClaims() {
        var s = analytics.summary();
        assertThat(s).containsKeys("dispute_fee_scenario_usd", "dispute_scenario_count")
                .doesNotContainKeys("expected_fees_avoided_usd", "expected_chargebacks_avoided");
        List<String> all = new ArrayList<>();
        keys(s, all);
        assertThat(all).noneMatch(k -> k.contains("avoided") || k.contains("saved"));
        assertThat(store.analyses.values()).allSatisfy(a -> assertThat(a.money == null || a.money.disputeFeeScenario() >= 0).isTrue());
    }

    @Test @SuppressWarnings("unchecked")
    void diagnosticsCountRoutesReasonsAndBlockedMoney() {
        var d = analytics.diagnostics();
        assertThat(d).containsKeys("resolved", "reasons", "synthetic_segments", "root_causes");
        var routes = (Map<String, Long>) d.get("routes");
        assertThat(routes).containsOnlyKeys("auto", "review", "investigate");
        assertThat(routes.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(store.analyses.size());
        long nonAuto = store.analyses.values().stream().filter(a -> !"auto".equals(a.decision.mode())).count();
        assertThat(routes.get("review") + routes.get("investigate")).isEqualTo(nonAuto);
        var reasons = (Map<String, Long>) d.get("review_reasons");
        assertThat(CODES).containsAll(reasons.keySet());
        assertThat(reasons).containsKeys("INJECTION", "DISPUTE_OPEN");
        assertThat(reasons.get("INJECTION")).isEqualTo(store.analyses.values().stream()
                .filter(a -> !"auto".equals(a.decision.mode()) && a.features.injection()).count());
        assertThat((Long) d.get("blocked_money_actions")).isPositive();
        assertThat(d.get("risk_status")).isEqualTo("UNVALIDATED");
    }

    @Test void approvingAReviewedRefundUnblocksIt() {
        long before = (Long) analytics.diagnostics().get("blocked_money_actions");
        var a = store.analyses.get("T11");
        assertThat(a.status).isEqualTo("needs_approval");
        pipeline.approve("T11", "agent", null, a.analysisId);
        assertThat((Long) analytics.diagnostics().get("blocked_money_actions")).isEqualTo(before - 1);
    }

    @Test void reasonPrefixesMapToCodes() {
        assertThat(AnalyticsService.reviewCode("risk_not_calibrated: no held-out bound")).isEqualTo("RISK_NOT_CALIBRATED");
        assertThat(AnalyticsService.reviewCode("above_auto_refund_limit")).isEqualTo("AMOUNT_OVER_LIMIT");
        assertThat(AnalyticsService.reviewCode("dispute_already_open: refunding now would pay twice")).isEqualTo("DISPUTE_OPEN");
        assertThat(AnalyticsService.reviewCode("injection caught by regex guard")).isEqualTo("INJECTION");
        assertThat(AnalyticsService.reviewCode("store_billing: only Apple/Google can refund or cancel")).isEqualTo("PROVIDER_UNSUPPORTED");
        assertThat(AnalyticsService.reviewCode("already_refunded: no second refund")).isEqualTo("ALREADY_REFUNDED");
        assertThat(AnalyticsService.reviewCode("ambiguous_subscriptions: 2 active contracts")).isEqualTo("AMBIGUOUS_SUBSCRIPTION");
        assertThat(AnalyticsService.reviewCode("provider_timeout: reconciling")).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(AnalyticsService.reviewCode("autonomy_gate_human")).isEqualTo("GATE_HUMAN");
        assertThat(AnalyticsService.reviewCode("outside_refund_window")).isEqualTo("OTHER");
    }

    @Test void crossRailRootCauseHasAFix() {
        assertThat(AnalyticsService.FIXES).containsKey("cross_rail_cancellation_mismatch");
    }
}
