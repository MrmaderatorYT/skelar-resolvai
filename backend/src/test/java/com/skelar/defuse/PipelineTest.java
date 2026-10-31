package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.dto.TicketAnalysis;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.executor.BillingException;
import com.skelar.defuse.service.executor.MockBillingService;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** End-to-end in gold mode (no API): executor, idempotency, approval, safety rules. */
@SpringBootTest
class PipelineTest {
    @Autowired InMemoryStore store;
    @Autowired TicketPipelineService pipeline;
    @Autowired AuditService audit;
    @Autowired MockBillingService billing;
    @Autowired ContextBuilderService contexts;

    @BeforeEach
    void reset() {
        store.reset();
        audit.clear();
    }

    private String cid(String ticketId) {
        return store.tickets.get(ticketId).customerId;
    }

    private Payment lastPayment(String ticketId) {
        List<Payment> pays = store.payments(cid(ticketId));
        return pays.get(pays.size() - 1);
    }

    @Test
    void sarcasticTrialChargeIsCancelledAndRefundedAutomatically() {
        TicketAnalysis a = pipeline.analyze("T01", false);
        assertThat(a.status).isEqualTo("resolved");
        assertThat(store.subscription(cid("T01")).status).isEqualTo("canceled");
        assertThat(lastPayment("T01").status).isEqualTo("refunded");
        assertThat(a.money.amountAtRisk()).isEqualTo(49.99);
    }

    @Test
    void reanalysisNeverRefundsTwice() {
        pipeline.analyze("T01", false);
        pipeline.analyze("T01", true);
        long refunds = audit.all().stream()
                .filter(e -> "executed".equals(e.get("event")) && "refund_full".equals(e.get("action"))).count();
        assertThat(refunds).isEqualTo(1);
    }

    @Test
    void largeRefundWaitsForApprovalButCancelIsImmediate() {
        TicketAnalysis a = pipeline.analyze("T11", false);
        assertThat(a.status).isEqualTo("needs_approval");
        assertThat(store.subscription(cid("T11")).status).isEqualTo("canceled");
        assertThat(lastPayment("T11").status).isEqualTo("succeeded");
        pipeline.approve("T11", "agent", null, a.analysisId);
        assertThat(lastPayment("T11").status).isEqualTo("refunded");
        assertThat(store.analyses.get("T11").status).isEqualTo("resolved");
    }

    @Test
    void injectionChangesNothing() {
        String subStatus = store.subscription(cid("T29")).status;
        TicketAnalysis a = pipeline.analyze("T29", false);
        assertThat(a.status).isEqualTo("escalated");
        assertThat(a.executed).isEmpty();
        assertThat(store.subscription(cid("T29")).status).isEqualTo(subStatus);
        assertThat(lastPayment("T29").status).isEqualTo("succeeded");
    }

    @Test
    void openDisputeBuildsEvidenceAndKeepsMoney() {
        TicketAnalysis a = pipeline.analyze("T37", false);
        assertThat(a.status).isEqualTo("escalated");
        assertThat(lastPayment("T37").status).isEqualTo("disputed");
        assertThat(store.evidence).containsKey("T37");
    }

    @Test
    void duplicateChargeRefundsOnlyTheDuplicate() {
        pipeline.analyze("T32", false);
        List<Payment> pays = store.payments(cid("T32"));
        String day = pays.get(pays.size() - 1).createdAt.substring(0, 10);
        assertThat(pays.stream().filter(p -> p.createdAt.startsWith(day)).map(p -> p.status).sorted())
                .containsExactly("refunded", "succeeded");
    }

    @Test
    void storePurchasesAreNotRefunded() {
        pipeline.analyze("T15", false);
        assertThat(lastPayment("T15").status).isEqualTo("succeeded");
    }

    @Test
    void refundCannotExceedPayment() {
        var ctx = contexts.build(cid("T03"));
        var d = new com.skelar.defuse.model.dto.PolicyDecision(List.of("refund_full"), "auto",
                ctx.lastPayment().amount() + 1, 0, "low", "P3", new java.util.ArrayList<>());
        assertThatThrownBy(() -> billing.run("refund_full", ctx, d, "T03")).isInstanceOf(BillingException.class);
    }

    @Test
    void eligibleCustomerGetsOneOfferAndAcceptingAppliesDiscount() {
        TicketAnalysis a = pipeline.analyze("T39", false);
        assertThat(a.status).isEqualTo("offer_sent");
        assertThat(a.reply).contains("25%").contains("CANCEL");
        assertThat(store.customer(cid("T39")).offers12m).isEqualTo(1);
        pipeline.acceptOffer("T39");
        assertThat(store.subscription(cid("T39")).discountPercent).isEqualTo(25);
        assertThat(store.subscription(cid("T39")).status).isEqualTo("active");
        assertThat(store.analyses.get("T39").status).isEqualTo("resolved");
    }

    @Test
    void decliningTheOfferCancelsImmediatelyAndNoSecondOffer() {
        pipeline.analyze("T42", false);
        pipeline.declineOffer("T42");
        assertThat(store.subscription(cid("T42")).status).isEqualTo("canceled");
        assertThatThrownBy(() -> pipeline.declineOffer("T42")).isInstanceOf(IllegalStateException.class);
        var again = store.addTicket(cid("T42"), "please cancel", "chat");
        store.tickets.get(again.id).gold = store.tickets.get("T42").gold;   // same reason, now inside the cooldown
        assertThat(pipeline.analyze(again.id, false).decision.actions()).noneMatch(x -> x.startsWith("offer_"));
    }

    @Test
    void discountSeekersAndSecondAccountsGetAnHonestCancel() {
        assertThat(pipeline.analyze("T40", false).decision.reasons()).contains("retention_not_eligible: offer_cooldown");
        assertThat(pipeline.analyze("T41", false).decision.reasons()).contains("retention_not_eligible: identity_cluster_offer");
        assertThat(store.subscription(cid("T41")).status).isEqualTo("canceled");
    }

    @Test
    void newTicketInGoldModeGoesToHuman() {
        var t = store.addTicket("cus_0001", "hello", "chat");
        assertThat(pipeline.analyze(t.id, false).decision.mode()).isNotEqualTo("auto");
    }
}
