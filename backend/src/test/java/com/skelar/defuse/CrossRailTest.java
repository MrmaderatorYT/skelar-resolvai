package com.skelar.defuse;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skelar.defuse.model.domain.Subscription;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cross-rail (P4) contracts plus fixture 12: two live contracts and "cancel this".
 * Ask which one, list only the customer's own plans, touch nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrossRailTest {
    @Autowired MockMvc mvc;
    @Autowired InMemoryStore store;
    @Autowired AuditService audit;
    @Autowired TicketPipelineService pipeline;
    @Autowired ContextBuilderService contexts;
    @Autowired ReferencePolicyService policy;

    @BeforeEach void reset() { store.reset(); audit.clear(); }

    private Subscription secondContract(String customerId) {
        Subscription s = new Subscription();   // test-only: a second active card contract on the same account
        s.id = "sub_test_second"; s.customerId = customerId; s.plan = "annual"; s.price = 89.99; s.currency = "USD";
        s.status = "active"; s.processor = "card"; s.currentPeriodEnd = "2027-10-31T12:00:00+00:00";
        store.subscriptions.add(s);
        return s;
    }

    // ---- P4 cross-rail contracts (from main) ----

    @Test void reconcilesAndCancelsWebButWaitsForRefundApproval() {
        var ctx = contexts.build("cus_0195");
        assertThat(ctx.crossRail().storeSubscriptionId()).isEqualTo("sub_0195_store");
        assertThat(ctx.crossRail().cardSubscriptionId()).isEqualTo("sub_0195_web");
        assertThat(ctx.lastPayment().id()).isEqualTo("pay_0195_web");
        var a = pipeline.analyze("T45", false);
        assertThat(a.status).isEqualTo("needs_approval");
        assertThat(a.money.amountAtRisk()).isEqualTo(49.99);
        assertThat(a.decision.actions()).containsExactly("cancel", "refund_full", "store_cancel_instructions");
        assertThat(store.subscriptions.stream().filter(s -> s.id.equals("sub_0195_web")).findFirst().orElseThrow().status).isEqualTo("canceled");
        assertThat(store.payment("pay_0195_web").status).isEqualTo("succeeded");
        assertThat(store.payment("pay_0195_store").status).isEqualTo("succeeded");
        pipeline.approve("T45", "agent", null, a.analysisId);
        assertThat(store.payment("pay_0195_web").status).isEqualTo("refunded");
        assertThat(store.payment("pay_0195_store").status).isEqualTo("succeeded");
    }

    @Test void rejectsAmbiguousOrUnlinkedSubscriptions() {
        store.customer("cus_0195").linkedSubscriptions = null;
        assertThatThrownBy(() -> contexts.build("cus_0195")).isInstanceOf(IllegalStateException.class);
        assertThat(store.payment("pay_0195_web").status).isEqualTo("succeeded");
    }

    @Test void rejectsUnlinkedOrDisputedCardCharge() {
        store.payment("pay_0195_web").subscriptionId = null;
        assertThatThrownBy(() -> contexts.build("cus_0195")).isInstanceOf(IllegalStateException.class);
        store.reset();
        store.payment("pay_0195_web").status = "disputed";
        var a = pipeline.analyze("T45", false);
        assertThat(a.status).isEqualTo("escalated");
        assertThat(a.decision.refundAmount()).isZero();
        assertThat(store.subscription("cus_0195").status).isEqualTo("active");
    }

    @Test void changedCrossRailPaymentRequiresReviewAgain() {
        var a = pipeline.analyze("T45", false);
        store.payment("pay_0195_web").status = "disputed";
        assertThatThrownBy(() -> pipeline.approve("T45", "agent", null, a.analysisId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Billing context changed");
        assertThat(store.payment("pay_0195_web").status).isEqualTo("disputed");
    }

    @Test void refundOnCrossRailAccountIsHeldForHumanAndLeavesWebPlanAlone() {
        // a plain refund never stops the live web renewal; only an explicit cancel does (T45 cancel_refund)
        var f = new ClassificationFeatures("refund", "get back the card charge", false, false, false, false, false, "neutral", "en", 1.0, List.of(), "none");
        var d = policy.decide(f, contexts.build("cus_0195"), 1.0);
        assertThat(d.mode()).isEqualTo("suggest");
        assertThat(d.actions()).containsExactly("refund_full");
        assertThat(d.refundAmount()).isEqualTo(49.99);
        assertThat(d.reasons()).anyMatch(r -> r.startsWith("cancel_not_requested: "));
    }

    // ---- Fixture 12 (from the execution/analytics branch) ----

    @Test void twoActiveContractsAskForClarificationAndChangeNothing() throws Exception {
        var second = secondContract("cus_0001");
        var first = store.subscriptions.stream().filter(s -> s.customerId.equals("cus_0001") && s != second).findFirst().orElseThrow();
        var t = store.addTicket("cus_0001", "cancel this", "chat");

        var a = pipeline.analyze(t.id, false);
        assertThat(a.status).isEqualTo("escalated");
        assertThat(a.decision.actions()).containsExactly("ask_clarification");
        assertThat(a.decision.mode()).isEqualTo("escalate");
        assertThat(a.decision.reasons()).hasSize(1).allMatch(r -> r.startsWith("ambiguous_subscriptions: "));
        assertThat(a.context).isNull(); assertThat(a.autonomy).isNull(); assertThat(a.money).isNull();
        assertThat(a.executed).isEmpty();
        assertThat(a.reply).contains("Quarterly plan via web checkout ($49.99)").contains("Annual plan via web checkout ($89.99)")
                .doesNotContain("App Store").doesNotContain("Google Play");
        assertThat(first.status).isEqualTo("active");
        assertThat(second.status).isEqualTo("active");
        assertThat(store.executed.keySet()).noneMatch(k -> k.startsWith(t.id + ":"));

        mvc.perform(get("/api/tickets/" + t.id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.subscription").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.customer.context").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.analysis.decision.actions[0]").value("ask_clarification"))
                .andExpect(jsonPath("$.analysis.context").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/analytics/summary")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/diagnostics")).andExpect(status().isOk())
                .andExpect(jsonPath("$.review_reasons.AMBIGUOUS_SUBSCRIPTION").value(1))
                .andExpect(jsonPath("$.routes.investigate").value(1));
    }

    @Test void ambiguousTicketCannotBeApproved() throws Exception {
        secondContract("cus_0001");
        var t = store.addTicket("cus_0001", "cancel this", "chat");
        var a = pipeline.analyze(t.id, false);
        mvc.perform(post("/api/tickets/" + t.id + "/approve").contentType("application/json")
                        .content("{\"analysis_id\":\"" + a.analysisId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test void oneContractPerRailIsNotAmbiguous() {
        assertThat(pipeline.ambiguousSubscriptions("cus_0001")).isNull();
        store.subscriptions.stream().filter(s -> s.customerId.equals("cus_0001")).findFirst().orElseThrow().status = "canceled";
        secondContract("cus_0001");
        assertThat(pipeline.ambiguousSubscriptions("cus_0001")).isNull();   // one canceled + one active: scope is clear
    }
}
