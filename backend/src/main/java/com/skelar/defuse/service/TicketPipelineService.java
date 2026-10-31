package com.skelar.defuse.service;

import com.skelar.defuse.config.policy.PolicyVersion;
import com.skelar.defuse.model.domain.Subscription;
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
import com.skelar.defuse.service.review.ActionHashService;
import com.skelar.defuse.store.InMemoryStore;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private ActionHashService hashes;
    private PolicyVersion policyVersion;
    private Duration reviewTtl = Duration.ofMinutes(30);

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

    @Autowired
    void reviewBinding(ActionHashService hashes, PolicyVersion policyVersion,
                       @Value("${defuse.review.ttl-minutes:30}") long ttlMinutes) {
        this.hashes = hashes; this.policyVersion = policyVersion; this.reviewTtl = Duration.ofMinutes(ttlMinutes);
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

        String ambiguous = ambiguousSubscriptions(t.customerId);
        CustomerContext ctx = null;
        if (ambiguous == null) {
            try { ctx = contexts.build(t.customerId); }
            catch (IllegalStateException e) {   // e.g. two live contracts: never guess which one to touch
                if (e.getMessage() == null || !e.getMessage().contains("human review required")) throw e;
                ambiguous = e.getMessage();
            }
        }
        if (ambiguous != null) return clarify(t, f, check, ambiguous, started);
        PolicyDecision d = policy.decide(f, ctx, f.confidence());
        if (check.flagged() && !llm.injection()) d.reasons().add("injection caught by regex guard");
        String name = store.customer(t.customerId).name;

        TicketAnalysis a = start(t, f, check);
        a.context = ctx;
        a.decision = d;
        a.autonomy = policy.assess(f, ctx, d, f.confidence());
        a.money = money.atRisk(f, d, ctx);
        a.actionHash = hashes.hash(d, ctx);
        a.status = t.status = d.mode().equals("auto") && d.actions().stream().anyMatch(x -> x.startsWith("offer_"))
                ? "offer_sent" : STATUS.get(d.mode());
        var previous = store.analyses.put(ticketId, a);
        audit.log("analyzed", ticketId, Map.of("intent", f.intent(), "risk", d.riskBand(), "mode", d.mode(),
                "actions", d.actions(), "reasons", d.reasons(), "policy_version", a.policyVersion, "action_hash", a.actionHash));
        if (previous != null) a.executed.addAll(previous.executed);   // a re-analysis keeps the results of money already moved
        a.executed.addAll(executor.execute(ticketId, ActionExecutorService.actionsToRunNow(d), d, ctx, "defuse-auto")
                .stream().filter(r -> !r.containsKey("skipped")).toList());
        if (a.executed.stream().anyMatch(r -> r.containsKey("error"))) {   // never claim what billing refused
            d.reasons().add(timedOut(a.executed) ? TIMEOUT_REASON : "execution_failed: human review");
            PolicyDecision escalated = new PolicyDecision(d.actions(), "escalate", d.refundAmount(), d.riskScore(),
                    d.riskBand(), "P1", d.reasons());
            a.decision = escalated;
            a.status = t.status = "escalated";
        }
        a.reply = f.injection() ? replies.template(name, a.decision) : replies.write(t, f, ctx, a.decision, name, a.executed);   // written after execution, from actual results
        a.latencyMs = (System.nanoTime() - started) / 1_000_000;
        return a;
    }

    private TicketAnalysis start(Ticket t, ClassificationFeatures f, InjectionCheck check) {
        TicketAnalysis a = new TicketAnalysis();
        a.ticketId = t.id;
        a.llmMode=settings.mode(); a.llmProvider=settings.provider(); a.llmModel=settings.model();
        a.analysisId = java.util.UUID.randomUUID().toString();
        a.features = f;
        a.injectionGuard = check;
        a.policyVersion = policyVersion.get();
        a.reviewExpiresAt = Instant.now().plus(reviewTtl).truncatedTo(ChronoUnit.SECONDS).toString();
        return a;
    }

    /** Fixture 12: more than one live contract and no way to tell which one is meant. Ask, touch nothing. */
    private TicketAnalysis clarify(Ticket t, ClassificationFeatures f, InjectionCheck check, String why, long started) {
        List<String> reasons = new ArrayList<>(List.of(why.startsWith("ambiguous_subscriptions") ? why : "ambiguous_subscriptions: " + why));
        if (f.injection()) reasons.add("prompt_injection: no actions, human review");
        var risk = policy.riskScore(f, store.customer(t.customerId).contactsLast30d);
        PolicyDecision d = new PolicyDecision(List.of("ask_clarification"), "escalate", 0, risk.score(), risk.band(),
                f.injection() || risk.band().equals("high") ? "P1" : "P2", reasons);
        TicketAnalysis a = start(t, f, check);
        a.decision = d;
        a.actionHash = hashes.hash(d, null);
        a.status = t.status = "escalated";
        a.reply = clarifyReply(t.customerId);
        store.analyses.put(t.id, a);
        audit.log("analyzed", t.id, Map.of("intent", String.valueOf(f.intent()), "risk", d.riskBand(), "mode", d.mode(),
                "actions", d.actions(), "reasons", d.reasons(), "policy_version", a.policyVersion, "action_hash", a.actionHash));
        a.latencyMs = (System.nanoTime() - started) / 1_000_000;
        return a;
    }

    private static final String TIMEOUT_REASON = "provider_timeout: reconciling with the provider, no blind retry";
    private static final Map<String, String> RAIL = Map.of("card", "web checkout", "app_store", "App Store", "google_play", "Google Play");

    /** Lists only this customer's own contracts, by plan and payment rail; never another account's. */
    private String clarifyReply(String customerId) {
        List<Subscription> own = store.subscriptions.stream().filter(s -> customerId.equals(s.customerId)).toList();
        List<Subscription> live = own.stream().filter(s -> Set.of("active", "paused").contains(s.status)).toList();
        List<String> labels = (live.isEmpty() ? own : live).stream().map(s -> String.format(java.util.Locale.US, "%s plan via %s ($%.2f)",
                capitalize(s.plan), RAIL.getOrDefault(s.processor, String.valueOf(s.processor)), s.price)).distinct().toList();
        String first = store.customer(customerId).name.split(" ")[0];
        return "Hi " + first + ",\n\nWe found more than one subscription on your account: " + String.join("; ", labels)
                + ". Which one do you mean? Reply with the plan and we'll take care of it - nothing has been changed yet."
                + "\n\nCalmly Support";
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? "Unknown" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Two live contracts for one customer: which one "cancel this" means is unknown. Null when unambiguous. */
    public String ambiguousSubscriptions(String customerId) {
        long live = store.subscriptions.stream()
                .filter(s -> customerId.equals(s.customerId) && Set.of("active", "paused").contains(s.status)).count();
        return live > 1 ? "ambiguous_subscriptions: " + live + " active contracts; human review required" : null;
    }

    private static boolean timedOut(List<Map<String, Object>> rows) {
        return rows.stream().anyMatch(r -> Boolean.TRUE.equals(r.get("pending")));
    }

    public TicketAnalysis approve(String ticketId, String agent, String replyText, String analysisId) {
        return approve(ticketId, agent, replyText, analysisId, null);
    }

    public TicketAnalysis approve(String ticketId, String agent, String replyText, String analysisId, String actionHash) {
        synchronized (store) { return approveLocked(ticketId, agent, replyText, analysisId, actionHash); }
    }

    private TicketAnalysis approveLocked(String ticketId, String agent, String replyText, String analysisId, String actionHash) {
        TicketAnalysis a = analysis(ticketId);
        if ("replay".equals(a.llmMode)) throw new IllegalStateException("Replay is read-only; actions cannot be approved or executed");
        if (analysisId == null || !analysisId.equals(a.analysisId)) {
            throw new IllegalStateException("Analysis changed; review the latest decision before approving");
        }
        if (!a.status.equals("needs_approval")) {
            throw new IllegalStateException("ticket " + ticketId + " is " + a.status + ", nothing to approve");
        }
        if (a.reviewExpiresAt != null && !Instant.now().isBefore(Instant.parse(a.reviewExpiresAt))) {
            audit.log("approval_expired", ticketId, Map.of("by", agent, "policy_version", policyVersion.get()));
            throw new IllegalStateException("Review expired; reanalyze");
        }
        CustomerContext current = contexts.build(ticket(ticketId).customerId);
        // the approval covers one exact action on one exact billing state (spec G, fixture 8)
        String fresh = hashes.hash(a.decision, current);
        if (!fresh.equals(a.actionHash) || (actionHash != null && !actionHash.equals(fresh))) {
            audit.log("approval_stale", ticketId, Map.of("by", agent, "check", "action_hash", "policy_version", policyVersion.get()));
            throw new IllegalStateException("Billing context changed; reanalyze and review before approving");
        }
        boolean alreadyCancelled = store.executed.containsKey(ticketId + ":cancel");
        boolean ownCancel = alreadyCancelled && "canceled".equals(current.subscriptionStatus());
        // cross-rail carve-out: our own cancel moved the card contract to "canceled"; the reviewed state is the
        // reference for that one field. Any other change on either rail still fails the comparison below.
        var cross = current.crossRail();
        var reviewedCross = a.context.crossRail();
        if (ownCancel && cross != null && reviewedCross != null && cross.cardSubscriptionId().equals(reviewedCross.cardSubscriptionId())
                && cross.storeSubscriptionId().equals(reviewedCross.storeSubscriptionId()) && cross.storeStatus().equals(reviewedCross.storeStatus())
                && cross.cardPaymentId().equals(reviewedCross.cardPaymentId()) && "canceled".equals(cross.cardStatus()))
            cross = new CustomerContext.CrossRail(cross.storeSubscriptionId(), cross.storeStatus(), cross.cardSubscriptionId(), reviewedCross.cardStatus(), cross.cardPaymentId());
        // our own cancel and the merchant-wide 30-day offer budget (moved by other customers' tickets) are not
        // this customer's billing state; everything else must match what the reviewer saw
        current = new CustomerContext(current.customerId(), current.platform(), current.billingProcessor(), current.ltvUsd(),
                current.contactsLast30d(), current.refundsLast180d(), current.chargebacksLifetime(),
                ownCancel ? a.context.subscriptionStatus() : current.subscriptionStatus(),
                current.plan(), current.price(), current.lastPayment(), current.duplicatePaymentId(),
                current.sessionsSinceLastCharge(), current.cancelFlowAbandoned(), current.acquisitionChannel(),
                current.tenureMonths(), current.paidRenewals(), current.offers12m(), current.identityOffers12m(),
                a.context.retentionOffers30d(), current.inHoldout(), cross);
        if (!a.context.equals(current)) {
            audit.log("approval_stale", ticketId, Map.of("by", agent));
            throw new IllegalStateException("Billing context changed; reanalyze and review before approving");
        }
        List<Map<String, Object>> results = executor.execute(ticketId, a.decision.actions(), a.decision, a.context, agent);
        a.executed.addAll(results);
        if (results.stream().anyMatch(r -> r.containsKey("error"))) {
            if (timedOut(results)) a.decision.reasons().add(TIMEOUT_REASON);
            a.status = ticket(ticketId).status = "escalated";
            audit.log("approval_failed", ticketId, Map.of("by", agent, "action_hash", String.valueOf(a.actionHash)));
            return a;
        }
        // reply_text is plain text for the customer only; actions and amounts always come from the stored decision
        if (replyText != null && !replyText.isBlank()) a.reply = replyText;
        else a.reply = replies.write(ticket(ticketId),a.features,a.context,a.decision,store.customer(a.context.customerId()).name,a.executed);
        boolean offer = a.decision.actions().stream().anyMatch(x -> x.startsWith("offer_"));
        a.status = ticket(ticketId).status = offer ? "offer_sent" : "resolved";
        audit.log("approved", ticketId, Map.of("by", agent, "action_hash", String.valueOf(a.actionHash), "policy_version", String.valueOf(a.policyVersion),
                "amount_cents", ActionHashService.cents(a.decision.refundAmount())));
        return a;
    }

    /** Customer took the win-back offer: the renewal this ticket canceled comes back discounted, or paused instead. */
    public TicketAnalysis acceptOffer(String ticketId) {
        synchronized (store) {
            TicketAnalysis a = offerPending(ticketId);
            String apply = a.decision.actions().contains("offer_discount") ? "apply_discount" : "pause";
            return closeOffer(a, ticketId, apply, "offer_accepted");
        }
    }

    /** Customer said no: the cancel already ran at analysis, so nothing else executes; the ticket just closes. */
    public TicketAnalysis declineOffer(String ticketId) {
        synchronized (store) {
            TicketAnalysis a = offerPending(ticketId);
            // an older offer without the cancel-first step still owes the customer the cancel they asked for
            return closeOffer(a, ticketId, store.executed.containsKey(ticketId + ":cancel") ? null : "cancel", "offer_declined");
        }
    }

    private TicketAnalysis offerPending(String ticketId) {
        TicketAnalysis a = analysis(ticketId);
        if ("replay".equals(a.llmMode)) throw new IllegalStateException("Replay is read-only; actions cannot be approved or executed");
        if (!a.status.equals("offer_sent")) throw new IllegalStateException("ticket " + ticketId + " has no pending offer");
        return a;
    }

    private TicketAnalysis closeOffer(TicketAnalysis a, String ticketId, String action, String event) {
        List<Map<String, Object>> results = action == null ? List.of()
                : executor.execute(ticketId, List.of(action), a.decision, a.context, "customer");
        a.executed.addAll(results);
        boolean failed = results.stream().anyMatch(r -> r.containsKey("error"));
        a.status = ticket(ticketId).status = failed ? "escalated" : "resolved";
        a.reply = replies.offerOutcome(store.customer(a.context.customerId()).name, action == null ? "cancel" : action, failed);
        audit.log(event, ticketId, Map.of("by", "customer", "action", action == null ? "none" : action));
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
        audit.log("rejected", ticketId, Map.of("by", agent, "reason", reason == null ? "" : reason,
                "action_hash", String.valueOf(a.actionHash), "policy_version", String.valueOf(a.policyVersion)));
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
