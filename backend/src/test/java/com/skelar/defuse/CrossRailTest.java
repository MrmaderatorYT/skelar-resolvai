package com.skelar.defuse;

import static org.assertj.core.api.Assertions.*;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CrossRailTest {
    @Autowired InMemoryStore store;
    @Autowired TicketPipelineService pipeline;
    @Autowired ContextBuilderService contexts;
    @Autowired ReferencePolicyService policy;
    @BeforeEach void reset() { store.reset(); }
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
    @Test void refundOnCrossRailAccountIsHeldForHumanAndStopsWebPlan() {
        var f = new ClassificationFeatures("refund", "get back the card charge", false, false, false, false, false, "neutral", "en", 1.0, List.of(), "none");
        var d = policy.decide(f, contexts.build("cus_0195"), 1.0);
        assertThat(d.mode()).isEqualTo("suggest");
        assertThat(d.actions()).containsExactlyInAnyOrder("cancel", "refund_full", "store_cancel_instructions");
        assertThat(d.refundAmount()).isEqualTo(49.99);
    }
}
