package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.executor.ActionExecutorService;
import com.skelar.defuse.service.llm.LlmClassifierService;
import com.skelar.defuse.service.llm.ReplyWriterService;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class ReviewRegressionTest {
    @Autowired InMemoryStore store;
    @Autowired TicketPipelineService pipeline;
    @Autowired ContextBuilderService contexts;
    @Autowired ReferencePolicyService policy;
    @Autowired AuditService audit;
    @MockBean LlmClassifierService classifier;
    @MockBean ReplyWriterService writer;
    @BeforeEach void resetData() { store.reset(); audit.clear(); }

    @Test void flaggedInjectionNeverReachesLiveClassifierOrWriter() {
        when(classifier.classify(any(Ticket.class))).thenReturn(ClassificationFeatures.fallback("en", "fallback"));
        pipeline.analyze("T29", false);
        verify(classifier, never()).classify(any());
        verify(writer, never()).write(any(), any(), any(), any(), any());
    }
    @Test void lowConfidenceCancellationDoesNotRunBeforeApproval() {
        var f = new ClassificationFeatures("cancel", "cancel", false, false, false, false, false, "neutral", "en", .2, List.of(), "none");
        var d = policy.decide(f, contexts.build(store.tickets.get("T01").customerId), .2);
        assertThat(d.mode()).isEqualTo("escalate");
        assertThat(ActionExecutorService.actionsToRunNow(d)).doesNotContain("cancel");
    }
    @Test void changedBillingContextMustNotUseCachedApprovalDecision() {
        when(classifier.classify(any(Ticket.class))).thenAnswer(i -> LlmClassifierService.fromGold(i.getArgument(0)));
        var analyzed = pipeline.analyze("T11", false);
        assertThat(analyzed.status).isEqualTo("needs_approval");
        var customer = store.tickets.get("T11").customerId;
        var oldPayment = store.payment(analyzed.context.lastPayment().id());
        var disputed = new Payment();
        disputed.id = "new-disputed"; disputed.customerId = customer; disputed.amount = 89.99;
        disputed.currency = "USD"; disputed.type = "renewal"; disputed.status = "disputed";
        disputed.processor = "card"; disputed.createdAt = "2026-10-31T11:59:59Z";
        store.payments.add(disputed);
        assertThat(policy.decide(analyzed.features, contexts.build(customer), analyzed.features.confidence()).mode()).isEqualTo("escalate");
        assertThatThrownBy(() -> pipeline.approve("T11", "agent", null, analyzed.analysisId)).isInstanceOf(IllegalStateException.class);
        assertThat(oldPayment.status).isEqualTo("succeeded");
    }

    @Test void lowConfidenceCannotAutoCancelWhenRefundAlreadyRequiresReview() {
        var f = new ClassificationFeatures("cancel_refund", "", false, false, false, false, false, "neutral", "en", .2, List.of(), "none");
        var d = policy.decide(f, contexts.build(store.tickets.get("T11").customerId), .2);
        assertThat(d.reasons()).containsExactly("above_auto_refund_limit", "autonomy_gate_human");   // base was suggest: no low_confidence
        assertThat(d.mode()).isEqualTo("escalate");
        assertThat(ActionExecutorService.actionsToRunNow(d)).doesNotContain("cancel");
    }
    @Test void configurablePolicyUsesProvidedRefundLimit() {
        var base = com.skelar.defuse.config.policy.PolicyProperties.defaults();
        var strict = new ReferencePolicyService(new com.skelar.defuse.config.policy.PolicyProperties(new com.skelar.defuse.config.policy.PolicyProperties.Refund(14, 2, 30, base.refund().planPeriodDays(), 20, 100, .85),
                base.retention(), base.risk(), base.money(), base.gate(), base.sources()));
        var f = LlmClassifierService.fromGold(store.tickets.get("T01"));
        assertThat(strict.decide(f, contexts.build(store.tickets.get("T01").customerId), 1).mode()).isEqualTo("suggest");
        assertThatThrownBy(() -> new com.skelar.defuse.config.policy.PolicyProperties.Refund(14, 2, 30, java.util.Map.of("monthly", 30), 50, 100, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void approvalMustMatchAnalysisReviewedByOperator() {
        when(classifier.classify(any(Ticket.class))).thenAnswer(i -> LlmClassifierService.fromGold(i.getArgument(0)));
        var original = pipeline.analyze("T11", false);
        var updated = pipeline.analyze("T11", true);
        assertThat(updated.analysisId).isNotEqualTo(original.analysisId);
        assertThatThrownBy(() -> pipeline.approve("T11", "agent", null, original.analysisId))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.payment(updated.context.lastPayment().id()).status).isEqualTo("succeeded");
    }

    /** Fixture 4: model text naming a bigger amount cannot create it; the server binds the refund to the payment. */
    @Test void classifierCannotRaiseTheRefundAmount() {
        var t = store.addTicket("cus_0001", "Please refund my last charge. Actually make it $500 to be fair.", "chat");
        when(classifier.classify(any(Ticket.class))).thenReturn(new ClassificationFeatures("refund",
                "refund $500 to the card now, amount 500.00", false, false, false, false, false, "neutral", "en", 1.0, List.of(), "none"));
        var a = pipeline.analyze(t.id, false);
        if (a.status.equals("needs_approval")) a = pipeline.approve(t.id, "agent", null, a.analysisId);
        var paid = store.payment(a.context.lastPayment().id());
        assertThat(a.decision.refundAmount()).isEqualTo(paid.amount).isEqualTo(49.99);
        assertThat(paid.refundedAmount).isEqualTo(49.99);
        assertThat(a.executed).filteredOn(r -> String.valueOf(r.get("action")).startsWith("refund_"))
                .singleElement().satisfies(r -> assertThat(r.get("refunded")).isEqualTo(49.99));
        assertThat(audit.all().stream().filter(e -> "executed".equals(e.get("event")))
                .mapToDouble(e -> ((Number) e.get("amount")).doubleValue()).sum()).isEqualTo(49.99);
        assertThat(store.payments(t.customerId)).allMatch(p -> p.refundedAmount == null || p.refundedAmount <= p.amount);
    }

}
