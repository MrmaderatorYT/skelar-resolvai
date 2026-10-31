package com.skelar.defuse.service.executor;

import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.store.InMemoryStore;
import com.skelar.defuse.registry.ActionRegistry;
import com.skelar.defuse.registry.BillingRegistry;
import com.skelar.defuse.spi.ActionContext;
import com.skelar.defuse.spi.Capability;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Runs policy-approved actions against billing. Idempotent per ticketId:action. */
@Service
public class ActionExecutorService {
    private final InMemoryStore store;
    private final BillingRegistry billing;
    private final ActionRegistry handlers;
    private final AuditService audit;

    public ActionExecutorService(InMemoryStore store, BillingRegistry billing, ActionRegistry handlers, AuditService audit) {
        this.store = store;
        this.billing = billing;
        this.handlers = handlers;
        this.audit = audit;
    }

    /** auto: everything. suggest: cancel the customer asked for (moves no money, so it
     *  never waits for a human) + safe actions.
     *  escalate: safe actions only. */
    public static List<String> actionsToRunNow(PolicyDecision d) {
        if (d.mode().equals("auto")) return d.actions();
        List<String> now = new ArrayList<>(d.actions().stream().filter(MockBillingService.SAFE_ACTIONS::contains).toList());
        if (d.mode().equals("suggest") && !d.reasons().contains("low_confidence") && d.reasons().stream().noneMatch(r -> r.startsWith("autonomy_gate_")) && d.actions().contains("cancel")) now.add("cancel");
        return now;
    }

    public List<Map<String, Object>> execute(String ticketId, List<String> actions, PolicyDecision d,
                                              CustomerContext ctx, String by) {
        synchronized (store) { return executeLocked(ticketId, actions, d, ctx, by); }
    }
    private List<Map<String, Object>> executeLocked(String ticketId, List<String> actions, PolicyDecision d,
                                                    CustomerContext ctx, String by) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String action : actions) {
            String key = ticketId + ":" + action;
            if (store.executed.containsKey(key)) {
                results.add(Map.of("action", action, "skipped", "already executed"));
                continue;
            }
            Map<String, Object> result;
            InMemoryStore.MutationSnapshot snapshot = null;
            try {
                snapshot = store.snapshot(ctx.customerId());
                var handler = handlers.forAction(action);
                String processor = ctx.billingProcessor();
                if (MockBillingService.MONEY_ACTIONS.contains(action)) {
                    String paymentId = action.equals("refund_duplicate") ? ctx.duplicatePaymentId() : ctx.lastPayment().id();
                    if (paymentId == null) throw new BillingException("No duplicate payment found");
                    processor = store.payment(paymentId).processor;
                }
                var provider = billing.forProcessor(processor);
                Capability required = handler.requiredCapability();
                if ((handler.movesMoney() && required == null)
                        || (required != null && !provider.capabilities().contains(required)))
                    throw new BillingException("Provider " + processor + " lacks capability for " + action);
                result = handler.run(new ActionContext(ticketId,ctx,d,provider));
                store.persistAction(ticketId, action, ctx, d.refundAmount());
            } catch (RuntimeException e) {
                if (snapshot != null) snapshot.restore();
                if (store.tickets.containsKey(ticketId)) store.tickets.get(ticketId).status = "escalated";
                if (store.analyses.containsKey(ticketId)) store.analyses.get(ticketId).status = "escalated";
                String error = e instanceof BillingException && e.getMessage()!=null ? e.getMessage() : "Plugin failed: " + e.getClass().getSimpleName();
                audit.log("action_failed", ticketId, Map.of("action", action, "by", by, "error", error));
                results.add(Map.of("action", action, "error", error));
                continue;
            }
            store.executed.put(key, result);
            double amount = MockBillingService.MONEY_ACTIONS.contains(action) ? d.refundAmount() : 0;
            audit.log("executed", ticketId, Map.of("action", action, "by", by, "result", result, "amount", amount));
            Map<String, Object> row = new LinkedHashMap<>(result);
            row.put("action", action);
            results.add(row);
        }
        return results;
    }
}
