package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.plugins.billing.MockCardBillingProvider;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.executor.ActionExecutorService;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.spi.BillingCommand;
import com.skelar.defuse.store.InMemoryStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Fixture 9: the refund is applied remotely, then the call times out. Reconcile; never send it twice. */
@SpringBootTest
class ProviderTimeoutTest {
    @Autowired InMemoryStore store;
    @Autowired AuditService audit;
    @Autowired TicketPipelineService pipeline;
    @Autowired ActionExecutorService executor;
    @Autowired ContextBuilderService contexts;
    @Autowired MockCardBillingProvider card;

    @BeforeEach void reset() { store.reset(); audit.clear(); }
    @AfterEach void clearHook() { card.timeoutAfterApply(null, false); }

    private long events(String event, String action) {
        return audit.all().stream().filter(e -> event.equals(e.get("event")) && action.equals(e.get("action"))).count();
    }

    @Test void unseenTimeoutIsParkedAsPendingAndNeverRetried() {
        var a = pipeline.analyze("T11", false);
        assertThat(a.status).isEqualTo("needs_approval");
        var payment = store.payment(a.context.lastPayment().id());
        double ltv = store.customer(a.context.customerId()).ltvUsd;
        card.timeoutAfterApply("refund_full", false);
        var after = pipeline.approve("T11", "agent", null, a.analysisId);

        assertThat(after.status).isEqualTo("escalated");
        assertThat(store.tickets.get("T11").status).isEqualTo("escalated");
        assertThat(after.executed).anySatisfy(r -> assertThat(r).containsEntry("action", "refund_full")
                .containsEntry("pending", true).containsEntry("error", "provider_timeout: reconciling"));
        assertThat(store.executed.get("T11:refund_full")).containsEntry("pending", true);
        assertThat(after.decision.reasons()).anyMatch(r -> r.startsWith("provider_timeout:"));
        assertThat(payment.status).isEqualTo("refunded");   // the provider did apply it
        assertThat(events("executed", "refund_full")).isZero();   // but it was never confirmed

        assertThatThrownBy(() -> pipeline.approve("T11", "agent", null, a.analysisId)).isInstanceOf(IllegalStateException.class);
        var again = pipeline.analyze("T11", true);
        if (again.status.equals("needs_approval")) pipeline.approve("T11", "agent", null, again.analysisId);
        assertThat(events("action_requested", "refund_full")).isEqualTo(1);
        assertThat(store.customer(a.context.customerId()).ltvUsd).isEqualTo(Math.round((ltv - payment.amount) * 100.0) / 100.0);
    }

    @Test void recordedTimeoutIsReconciledByLookup() {
        var a = pipeline.analyze("T11", false);
        card.timeoutAfterApply("refund_full", true);
        var after = pipeline.approve("T11", "agent", null, a.analysisId);

        assertThat(after.status).isEqualTo("resolved");
        assertThat(after.executed).anySatisfy(r -> assertThat(r).containsEntry("action", "refund_full").containsEntry("reconciled", true));
        assertThat(store.executed.get("T11:refund_full")).containsEntry("reconciled", true);
        assertThat(events("executed", "refund_full")).isEqualTo(1);
        assertThat(events("verified", "refund_full")).isEqualTo(1);
        pipeline.analyze("T11", true);
        assertThat(events("action_requested", "refund_full")).isEqualTo(1);
    }

    @Test void pendingKeyIsNotRetriedByTheExecutor() {
        var ctx = contexts.build("cus_0001");
        var d = new PolicyDecision(List.of("refund_full"), "auto", ctx.lastPayment().amount(), 0, "low", "P3", new ArrayList<>());
        card.timeoutAfterApply("refund_full", false);
        var first = executor.execute("T01", d.actions(), d, ctx, "test");
        var second = executor.execute("T01", d.actions(), d, ctx, "test");
        assertThat(first.get(0)).containsEntry("pending", true);
        assertThat(second.get(0)).containsEntry("pending", true);
        assertThat(events("action_requested", "refund_full")).isEqualTo(1);
    }

    @Test void providerKeyBindsAmountAndKeepsTheLocalKey() {
        var ctx = contexts.build("cus_0001");
        var d1 = new PolicyDecision(List.of("refund_full"), "auto", 49.99, 0, "low", "P3", new ArrayList<>());
        var d2 = new PolicyDecision(List.of("refund_full"), "auto", 10.00, 0, "low", "P3", new ArrayList<>());
        var c1 = new BillingCommand("T01", "refund_full", ctx, d1, "T01:refund_full");
        var c2 = new BillingCommand("T01", "refund_full", ctx, d2, "T01:refund_full");
        assertThat(c1.providerKey()).matches("[0-9a-f]{64}").isNotEqualTo(c2.providerKey());
        assertThat(c1.providerKey()).isEqualTo(new BillingCommand("T01", "refund_full", ctx, d1, "T01:refund_full").providerKey());
        assertThatThrownBy(() -> new BillingCommand("T01", "refund_full", ctx, d1, "other")).isInstanceOf(IllegalArgumentException.class);
    }
}
