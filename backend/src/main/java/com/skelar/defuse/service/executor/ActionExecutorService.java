package com.skelar.defuse.service.executor;

import com.skelar.defuse.config.policy.PolicyVersion;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.model.dto.TicketAnalysis;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.review.ActionHashService;
import com.skelar.defuse.store.InMemoryStore;
import com.skelar.defuse.registry.ActionRegistry;
import com.skelar.defuse.registry.BillingRegistry;
import com.skelar.defuse.spi.ActionContext;
import com.skelar.defuse.spi.BillingProvider;
import com.skelar.defuse.spi.BillingTimeoutException;
import com.skelar.defuse.spi.Capability;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Runs policy-approved actions against billing. Idempotent per ticketId:action; a provider timeout is
 *  reconciled by lookup and otherwise parked as pending, never retried blindly. */
@Service
public class ActionExecutorService {
    public static final String TIMEOUT_ERROR = "provider_timeout: reconciling";
    public static final String SQL_WRITE_FAILED = "sql_write_failed: reconcile";
    public static final String AMOUNT_REUSED = "idempotency_key_reused_with_different_amount";

    private final InMemoryStore store;
    private final BillingRegistry billing;
    private final ActionRegistry handlers;
    private final AuditService audit;
    private final PolicyVersion policyVersion;

    @Autowired
    public ActionExecutorService(InMemoryStore store, BillingRegistry billing, ActionRegistry handlers, AuditService audit,
                                 PolicyVersion policyVersion) {
        this.store = store;
        this.billing = billing;
        this.handlers = handlers;
        this.audit = audit;
        this.policyVersion = policyVersion;
    }

    public ActionExecutorService(InMemoryStore store, BillingRegistry billing, ActionRegistry handlers, AuditService audit) {
        this(store, billing, handlers, audit, null);
    }

    /** auto: everything. suggest: safe actions, plus the cancel the customer asked for (moves no money, so it
     *  never waits for a human), unless the intent itself is uncertain (low_confidence). escalate: safe actions only. */
    public static List<String> actionsToRunNow(PolicyDecision d) {
        if (d.mode().equals("auto")) return d.actions();
        List<String> now = new ArrayList<>(d.actions().stream().filter(MockBillingService.SAFE_ACTIONS::contains).toList());
        if (d.mode().equals("suggest") && !d.reasons().contains("low_confidence")) {
            for (String a : List.of("cancel", "store_cancel_instructions"))
                if (d.actions().contains(a) && !now.contains(a)) now.add(a);
        }
        return now;
    }

    public List<Map<String, Object>> execute(String ticketId, List<String> actions, PolicyDecision d,
                                              CustomerContext ctx, String by) {
        synchronized (store) { return executeLocked(ticketId, actions, d, ctx, by); }
    }
    private List<Map<String, Object>> executeLocked(String ticketId, List<String> actions, PolicyDecision d,
                                                    CustomerContext ctx, String by) {
        List<Map<String, Object>> results = new ArrayList<>();
        TicketAnalysis analysis = store.analyses.get(ticketId);
        String version = analysis != null && analysis.policyVersion != null ? analysis.policyVersion
                : policyVersion == null ? "unknown" : policyVersion.get();
        String actionHash = analysis == null ? null : analysis.actionHash;
        for (String action : actions) {
            String key = ticketId + ":" + action;
            boolean money = MockBillingService.MONEY_ACTIONS.contains(action);
            long cents = money ? ActionHashService.cents(d.refundAmount()) : 0;
            var command = new ActionContext(ticketId, ctx, d, null).command(action);
            Map<String, Object> previous = store.executed.get(key);
            if (previous != null) {
                // one local key is one operation: a different amount is a new proposal, never a silent skip (spec H)
                if (!Objects.equals(previous.get("amount_cents"), cents)) {
                    escalate(ticketId);
                    audit.log("action_failed", ticketId, fields("action", action, "by", by, "error", AMOUNT_REUSED,
                            "amount_cents", cents, "previous_amount_cents", previous.get("amount_cents")));
                    results.add(fields("action", action, "error", AMOUNT_REUSED));
                    continue;
                }
                if (!Boolean.TRUE.equals(previous.get("pending"))) {
                    results.add(Map.of("action", action, "skipped", "already executed"));
                    continue;
                }
                // a timed-out call is never sent again: ask the provider first (spec H, fixture 9)
                Optional<Map<String, Object>> found = lookup(action, ctx, command.providerKey());
                if (found.isEmpty()) {
                    results.add(withAction(previous, action));
                    continue;
                }
                Map<String, Object> reconciled = new LinkedHashMap<>(found.get());
                reconciled.put("reconciled", true);
                reconciled.put("amount_cents", cents);
                store.executed.put(key, reconciled);
                audit.log("executed", ticketId, fields("action", action, "by", by, "result", reconciled,
                        "amount", money ? d.refundAmount() : 0, "policy_version", version, "action_hash", actionHash, "amount_cents", cents));
                results.add(withAction(reconciled, action));
                continue;
            }
            Map<String, Object> result = null;
            InMemoryStore.MutationSnapshot snapshot = null;
            BillingProvider provider = null;
            String paymentId = null;
            boolean billingCall = false;
            boolean providerActed = false;   // once true, the provider holds the effect and local state is never rolled back
            try {
                snapshot = store.snapshot(ctx.customerId());
                var handler = handlers.forAction(action);
                String processor = ctx.billingProcessor();
                if (money) {
                    paymentId = action.equals("refund_duplicate") ? ctx.duplicatePaymentId() : ctx.lastPayment().id();
                    if (paymentId == null) throw new BillingException("No duplicate payment found");
                    processor = store.payment(paymentId).processor;
                }
                provider = billing.forProcessor(processor);
                Capability required = handler.requiredCapability();
                if ((handler.movesMoney() && required == null)
                        || (required != null && !provider.capabilities().contains(required)))
                    throw new BillingException("Provider " + processor + " lacks capability for " + action);
                billingCall = required != null;
                if (billingCall) audit.log("action_requested", ticketId, fields("action", action, "by", by, "policy_version", version,
                        "action_hash", actionHash, "amount_cents", cents, "payment_id", paymentId, "provider_key", command.providerKey()));
                try {
                    result = handler.run(new ActionContext(ticketId, ctx, d, provider));
                } catch (BillingTimeoutException e) {
                    // the provider may have applied it: ask, do not undo or repeat. A failing lookup is also "unknown".
                    Optional<Map<String, Object>> found = lookup(action, ctx, command.providerKey());
                    if (found.isEmpty()) {
                        // parked, not failed: the snapshot is deliberately kept, because the provider may hold the effect
                        Map<String, Object> row = pending(action, cents);
                        store.executed.put(key, row);
                        escalate(ticketId);
                        audit.log("action_pending", ticketId, fields("action", action, "by", by, "error", TIMEOUT_ERROR,
                                "policy_version", version, "action_hash", actionHash, "amount_cents", cents));
                        results.add(row);
                        continue;
                    }
                    result = new LinkedHashMap<>(found.get());
                    result.put("reconciled", true);
                }
                providerActed = true;
                store.persistAction(ticketId, action, ctx, d.refundAmount());
            } catch (RuntimeException e) {
                if (providerActed) {
                    // the provider already applied it: rolling back the mock state would hide a real refund and invite a second one.
                    // Keep the provider result, record the failed local write, and hand the ticket to a human to reconcile.
                    Map<String, Object> row = new LinkedHashMap<>(result);
                    row.put("amount_cents", cents);
                    row.put("sql_write_failed", true);
                    row.put("error", SQL_WRITE_FAILED);
                    store.executed.put(key, row);
                    escalate(ticketId);
                    audit.log("sql_write_failed", ticketId, fields("action", action, "by", by, "error", SQL_WRITE_FAILED,
                            "cause", String.valueOf(e.getMessage()), "policy_version", version, "action_hash", actionHash,
                            "amount_cents", cents, "amount", money ? d.refundAmount() : 0));
                    results.add(withAction(row, action));
                    continue;
                }
                // a definite failure before the provider acted rolls back the local mutation, so the snapshot is restored
                if (snapshot != null) snapshot.restore();
                escalate(ticketId);
                String error = e instanceof BillingException && e.getMessage()!=null ? e.getMessage() : "Plugin failed: " + e.getClass().getSimpleName();
                audit.log("action_failed", ticketId, Map.of("action", action, "by", by, "error", error));
                results.add(Map.of("action", action, "error", error));
                continue;
            }
            Map<String, Object> stored = new LinkedHashMap<>(result);
            stored.put("amount_cents", cents);
            store.executed.put(key, stored);
            double amount = money ? d.refundAmount() : 0;
            audit.log("executed", ticketId, fields("action", action, "by", by, "result", stored, "amount", amount,
                    "policy_version", version, "action_hash", actionHash, "amount_cents", cents));
            if (billingCall) verify(ticketId, action, ctx, provider, paymentId, version, actionHash, cents);
            results.add(withAction(stored, action));
        }
        return results;
    }

    /** Remote outcome of a command by its provider key. A failing lookup is "unknown", never "failed". */
    private Optional<Map<String, Object>> lookup(String action, CustomerContext ctx, String providerKey) {
        try { return billing.forProcessor(processorFor(action, ctx)).lookup(providerKey); }
        catch (RuntimeException e) { return Optional.empty(); }
    }

    private String processorFor(String action, CustomerContext ctx) {
        if (!MockBillingService.MONEY_ACTIONS.contains(action)) return ctx.billingProcessor();
        String id = action.equals("refund_duplicate") ? ctx.duplicatePaymentId() : ctx.lastPayment().id();
        return id == null ? ctx.billingProcessor() : store.payment(id).processor;
    }

    /** Post-state check: re-read what the provider now reports, so the audit shows the effect, not just the call. */
    private void verify(String ticketId, String action, CustomerContext ctx, BillingProvider provider, String paymentId,
                        String version, String actionHash, long cents) {
        Map<String, Object> f = fields("action", action, "policy_version", version, "action_hash", actionHash, "amount_cents", cents);
        try {
            if (provider != null && paymentId != null) {
                var p = provider.payments(ctx.customerId()).stream().filter(x -> x.id.equals(paymentId)).findFirst().orElse(null);
                f.put("payment_id", paymentId);
                f.put("payment_status", p == null ? null : p.status);
                f.put("refunded_cents", p == null || p.refundedAmount == null ? null : ActionHashService.cents(p.refundedAmount));
            } else if (provider != null) {
                f.put("subscription_status", provider.subscription(ctx.customerId()).status);
            }
        } catch (RuntimeException e) {
            f.put("unverified", e.getClass().getSimpleName());
        }
        audit.log("verified", ticketId, f);
    }

    private void escalate(String ticketId) {
        if (store.tickets.containsKey(ticketId)) store.tickets.get(ticketId).status = "escalated";
        if (store.analyses.containsKey(ticketId)) store.analyses.get(ticketId).status = "escalated";
    }

    private static Map<String, Object> pending(String action, long cents) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("action", action);
        row.put("pending", true);
        row.put("error", TIMEOUT_ERROR);
        row.put("amount_cents", cents);
        return row;
    }

    private static Map<String, Object> withAction(Map<String, Object> row, String action) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("action", action);
        return out;
    }

    private static Map<String, Object> fields(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
