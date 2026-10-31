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

    public TicketPipelineService(InMemoryStore store, InjectionGuardService guard, LlmClassifierService classifier,
                                 ContextBuilderService contexts, ReferencePolicyService policy,
                                 ReplyWriterService replies, MoneyService money, ActionExecutorService executor,
                                 AuditService audit) {
        this.store = store;
        this.guard = guard;
        this.classifier = classifier;
        this.contexts = contexts;
        this.policy = policy;
        this.replies = replies;
        this.money = money;
        this.executor = executor;
        this.audit = audit;
    }

    public TicketAnalysis analyze(String ticketId, boolean force) {
        if (!force && store.analyses.containsKey(ticketId)) return store.analyses.get(ticketId);
        Ticket t = ticket(ticketId);
        long started = System.nanoTime();

        InjectionCheck check = guard.check(t.text);
        ClassificationFeatures llm = classifier.classify(t);
        ClassificationFeatures f = llm.withInjection(llm.injection() || check.flagged());

        CustomerContext ctx = contexts.build(t.customerId);
        PolicyDecision d = policy.decide(f, ctx, f.confidence());
        if (check.flagged() && !llm.injection()) d.reasons().add("injection caught by regex guard");
        String name = store.customer(t.customerId).name;

        TicketAnalysis a = new TicketAnalysis();
        a.ticketId = ticketId;
        a.features = f;
        a.injectionGuard = check;
        a.context = ctx;
        a.decision = d;
        a.money = money.atRisk(f, d, ctx);
        a.status = t.status = STATUS.get(d.mode());
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
        a.reply = replies.write(t, f, ctx, a.decision, name);   // written after execution, from actual results
        a.latencyMs = (System.nanoTime() - started) / 1_000_000;
        return a;
    }

    public TicketAnalysis approve(String ticketId, String agent, String replyText) {
        TicketAnalysis a = analysis(ticketId);
        if (!a.status.equals("needs_approval")) {
            throw new IllegalStateException("ticket " + ticketId + " is " + a.status + ", nothing to approve");
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
        a.status = ticket(ticketId).status = "resolved";
        audit.log("approved", ticketId, Map.of("by", agent));
        return a;
    }

    public TicketAnalysis reject(String ticketId, String agent, String reason) {
        TicketAnalysis a = analysis(ticketId);
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
