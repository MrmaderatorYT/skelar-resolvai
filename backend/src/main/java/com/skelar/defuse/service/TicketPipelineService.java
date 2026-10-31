package com.skelar.defuse.service;

import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.InjectionCheck;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.model.dto.TicketAnalysis;
import com.skelar.defuse.service.analytics.MoneyService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.executor.ActionExecutorService;
import com.skelar.defuse.service.guard.InjectionGuardService;
import com.skelar.defuse.service.llm.LlmClassifierService;
import com.skelar.defuse.service.llm.ReplyWriterService;
import com.skelar.defuse.service.llm.ReplayService;
import com.skelar.defuse.service.llm.LlmSettings;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Ticket -> guard -> classify -> context -> policy -> reply -> execute. The LLM never moves money. */
@Service
public class TicketPipelineService {
    private static final Map<String, String> STATUS =
            Map.of("auto", "resolved", "suggest", "needs_approval", "escalate", "escalated");

    private final InMemoryStore store;
    private final InjectionGuardService guard;
    private final LlmClassifierService classifier;
    private final ContextBuilderService contexts;
    private final ReferencePolicyService policy;
    private final ReplyWriterService replies;
    private final MoneyService money;
    private final ActionExecutorService executor;
    private final AuditService audit;
    private final ReplayService replay;
    private final LlmSettings settings;

    public TicketPipelineService(InMemoryStore store, InjectionGuardService guard, LlmClassifierService classifier,
                                 ContextBuilderService contexts, ReferencePolicyService policy,
                                 ReplyWriterService replies, MoneyService money, ActionExecutorService executor,
                                 AuditService audit, ReplayService replay, LlmSettings settings) {
        this.store = store;
        this.guard = guard;
        this.classifier = classifier;
        this.contexts = contexts;
        this.policy = policy;
        this.replies = replies;
        this.money = money;
        this.executor = executor;
        this.audit = audit; this.replay = replay; this.settings = settings;
    }

    public TicketAnalysis analyze(String ticketId, boolean force) {
        synchronized (store) { return analyzeLocked(ticketId, force); }
    }

    private TicketAnalysis analyzeLocked(String ticketId, boolean force) {
        if (!force && store.analyses.containsKey(ticketId)) return store.analyses.get(ticketId);
        Ticket t = ticket(ticketId);
        if ("replay".equals(settings.mode())) return replay.restore(t);
        long started = System.nanoTime();

        InjectionCheck check = guard.check(t.text);
        ClassificationFeatures llm = check.flagged()
                ? ClassificationFeatures.fallback(t.language, "prompt injection: human review").withInjection(true)
                : classifier.classify(t);
        ClassificationFeatures f = llm.withInjection(llm.injection() || check.flagged());

        CustomerContext ctx = contexts.build(t.customerId);
        PolicyDecision d = policy.decide(f, ctx, f.confidence());
        if (check.flagged() && !llm.injection()) d.reasons().add("injection caught by regex guard");
        String name = store.customer(t.customerId).name;

        TicketAnalysis a = new TicketAnalysis();
        a.ticketId = ticketId;
        a.llmMode=settings.mode(); a.llmProvider=settings.provider(); a.llmModel=settings.model();
        a.analysisId = java.util.UUID.randomUUID().toString();
        a.features = f;
        a.injectionGuard = check;
        a.context = ctx;
        a.decision = d;
        a.autonomy = policy.assess(f, ctx, d, f.confidence());
        a.money = money.atRisk(f, d, ctx);
        a.status = t.status = d.mode().equals("auto") && d.actions().stream().anyMatch(x -> x.startsWith("offer_"))
                ? "offer_sent" : STATUS.get(d.mode());
        store.analyses.put(ticketId, a);
        audit.log("analyzed", ticketId, Map.of("intent", f.intent(), "risk", d.riskBand(), "mode", d.mode(),
                "actions", d.actions(), "reasons", d.reasons()));
        a.executed.addAll(executor.execute(ticketId, ActionExecutorService.actionsToRunNow(d), d, ctx, "defuse-auto"));
        if (a.executed.stream().anyMatch(r -> r.containsKey("error"))) {   // never claim what billing refused
            d.reasons().add("execution_failed: human review");
            PolicyDecision escalated = new PolicyDecision(d.actions(), "escalate", d.refundAmount(), d.riskScore(),
                    d.riskBand(), "P1", d.reasons());
            a.decision = escalated;
            a.status = t.status = "escalated";
        }
        a.reply = f.injection() ? replies.template(name, a.decision) : replies.write(t, f, ctx, a.decision, name, a.executed);   // written after execution, from actual results
        a.latencyMs = (System.nanoTime() - started) / 1_000_000;
        return a;
    }

    public TicketAnalysis approve(String ticketId, String agent, String replyText, String analysisId) {
        synchronized (store) { return approveLocked(ticketId, agent, replyText, analysisId); }
    }

    private TicketAnalysis approveLocked(String ticketId, String agent, String replyText, String analysisId) {
        TicketAnalysis a = analysis(ticketId);
        if ("replay".equals(a.llmMode)) throw new IllegalStateException("Replay is read-only; actions cannot be approved or executed");
        if (analysisId == null || !analysisId.equals(a.analysisId)) {
            throw new IllegalStateException("Analysis changed; review the latest decision before approving");
        }
        if (!a.status.equals("needs_approval")) {
            throw new IllegalStateException("ticket " + ticketId + " is " + a.status + ", nothing to approve");
        }
        CustomerContext current = contexts.build(ticket(ticketId).customerId);
        boolean alreadyCancelled = store.executed.containsKey(ticketId + ":cancel");
        if (alreadyCancelled && "canceled".equals(current.subscriptionStatus())) {
            var cross = current.crossRail();
            var reviewed = a.context.crossRail();
            if (cross != null && reviewed != null && cross.cardSubscriptionId().equals(reviewed.cardSubscriptionId())
                    && cross.storeSubscriptionId().equals(reviewed.storeSubscriptionId()) && cross.storeStatus().equals(reviewed.storeStatus())
                    && cross.cardPaymentId().equals(reviewed.cardPaymentId()) && "canceled".equals(cross.cardStatus()))
                cross = new CustomerContext.CrossRail(cross.storeSubscriptionId(), cross.storeStatus(), cross.cardSubscriptionId(), reviewed.cardStatus(), cross.cardPaymentId());
            current = new CustomerContext(current.customerId(), current.platform(), current.billingProcessor(), current.ltvUsd(),
                    current.contactsLast30d(), current.refundsLast180d(), current.chargebacksLifetime(), a.context.subscriptionStatus(),
                    current.plan(), current.price(), current.lastPayment(), current.duplicatePaymentId(),
                    current.sessionsSinceLastCharge(), current.cancelFlowAbandoned(), current.acquisitionChannel(),
                    current.tenureMonths(), current.paidRenewals(), current.offers12m(), current.identityOffers12m(),
                    current.retentionOffers30d(), current.inHoldout(), cross);
        }
        if (!a.context.equals(current)) {
            audit.log("approval_stale", ticketId, Map.of("by", agent));
            throw new IllegalStateException("Billing context changed; reanalyze and review before approving");
        }
        List<Map<String, Object>> results = executor.execute(ticketId, a.decision.actions(), a.decision, a.context, agent);
        a.executed.addAll(results);
        if (results.stream().anyMatch(r -> r.containsKey("error"))) {
            a.status = ticket(ticketId).status = "escalated";
            audit.log("approval_failed", ticketId, Map.of("by", agent));
            return a;
        }
        // reply_text is plain text for the customer only; actions and amounts always come from the stored decision
        if (replyText != null && !replyText.isBlank()) a.reply = replyText;
        else a.reply = replies.write(ticket(ticketId),a.features,a.context,a.decision,store.customer(a.context.customerId()).name,a.executed);
        boolean offer = a.decision.actions().stream().anyMatch(x -> x.startsWith("offer_"));
        a.status = ticket(ticketId).status = offer ? "offer_sent" : "resolved";
        audit.log("approved", ticketId, Map.of("by", agent));
        return a;
    }

    /** Customer accepted the save offer: apply the discount or the pause. */
    public TicketAnalysis acceptOffer(String ticketId) {
        TicketAnalysis a = offerPending(ticketId);
        String apply = a.decision.actions().contains("offer_discount") ? "apply_discount" : "pause";
        return closeOffer(a, ticketId, apply, "offer_accepted");
    }

    /** Customer said no (or repeated "cancel"): cancel right away, no second offer. */
    public TicketAnalysis declineOffer(String ticketId) {
        return closeOffer(offerPending(ticketId), ticketId, "cancel", "offer_declined");
    }

    private TicketAnalysis offerPending(String ticketId) {
        TicketAnalysis a = analysis(ticketId);
        if ("replay".equals(a.llmMode)) throw new IllegalStateException("Replay is read-only; actions cannot be approved or executed");
        if (!a.status.equals("offer_sent")) throw new IllegalStateException("ticket " + ticketId + " has no pending offer");
        return a;
    }

    private TicketAnalysis closeOffer(TicketAnalysis a, String ticketId, String action, String event) {
        List<Map<String, Object>> results = executor.execute(ticketId, List.of(action), a.decision, a.context, "customer");
        a.executed.addAll(results);
        boolean failed = results.stream().anyMatch(r -> r.containsKey("error"));
        a.status = ticket(ticketId).status = failed ? "escalated" : "resolved";
        a.reply = replies.offerOutcome(store.customer(a.context.customerId()).name, action, failed);
        audit.log(event, ticketId, Map.of("by", "customer", "action", action));
        return a;
    }

    public TicketAnalysis humanReply(String ticketId, String text, String agent) {
        synchronized (store) {
            TicketAnalysis a = analysis(ticketId);
            if ("replay".equals(a.llmMode)) throw new IllegalStateException("Replay is read-only");
            if (!List.of("needs_approval", "escalated").contains(a.status)) throw new IllegalStateException("Ticket is not awaiting a human");
            if (text == null || text.isBlank()) throw new IllegalArgumentException("Reply is required");
            a.reply = text.trim(); a.status = ticket(ticketId).status = "resolved";
            audit.log("human_reply", ticketId, Map.of("by", agent)); return a;
        }
    }

    public TicketAnalysis reject(String ticketId, String agent, String reason) {
        synchronized (store) { return rejectLocked(ticketId, agent, reason); }
    }

    private TicketAnalysis rejectLocked(String ticketId, String agent, String reason) {
        TicketAnalysis a = analysis(ticketId);
        if ("replay".equals(a.llmMode)) throw new IllegalStateException("Replay is read-only; actions cannot be approved or executed");
        a.status = ticket(ticketId).status = "escalated";
        audit.log("rejected", ticketId, Map.of("by", agent, "reason", reason == null ? "" : reason));
        return a;
    }

    private Ticket ticket(String id) {
        Ticket t = store.tickets.get(id);
        if (t == null) throw new NoSuchElementException("ticket " + id + " not found");
        return t;
    }

    private TicketAnalysis analysis(String id) {
        ticket(id);
        TicketAnalysis a = store.analyses.get(id);
        if (a == null) throw new IllegalStateException("analyze ticket " + id + " first");
        return a;
    }
}
