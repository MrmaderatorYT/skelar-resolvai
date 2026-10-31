package com.skelar.defuse.service.executor;

import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Runs policy-approved actions against billing. Idempotent per ticketId:action. */
@Service
public class ActionExecutorService {
    private final InMemoryStore store;
    private final MockBillingService billing;
    private final AuditService audit;

    public ActionExecutorService(InMemoryStore store, MockBillingService billing, AuditService audit) {
        this.store = store;
        this.billing = billing;
        this.audit = audit;
    }

    /** auto: everything. suggest: cancel the customer asked for (moves no money, so it
     *  never waits for a human) + safe actions.
     *  escalate: safe actions only. */
    public static List<String> actionsToRunNow(PolicyDecision d) {
        if (d.mode().equals("auto")) return d.actions();
        List<String> now = new ArrayList<>(d.actions().stream().filter(MockBillingService.SAFE_ACTIONS::contains).toList());
        if (d.mode().equals("suggest") && !d.reasons().contains("low_confidence") && d.actions().contains("cancel")) now.add("cancel");
        return now;
    }

    public synchronized List<Map<String, Object>> execute(String ticketId, List<String> actions, PolicyDecision d,
                                                          CustomerContext ctx, String by) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String action : actions) {
            String key = ticketId + ":" + action;
            if (store.executed.containsKey(key)) {
                results.add(Map.of("action", action, "skipped", "already executed"));
                continue;
            }
            Map<String, Object> result;
            try {
                result = billing.run(action, ctx, d, ticketId);
            } catch (BillingException e) {
                audit.log("action_failed", ticketId, Map.of("action", action, "by", by, "error", e.getMessage()));
                results.add(Map.of("action", action, "error", e.getMessage()));
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
