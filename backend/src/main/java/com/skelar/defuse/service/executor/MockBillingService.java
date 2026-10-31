package com.skelar.defuse.service.executor;

import com.skelar.defuse.model.domain.Customer;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.domain.Subscription;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.store.InMemoryStore;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Mock billing provider: each action mutates the store the way Stripe/Solidgate would. */
@Service
public class MockBillingService {
    public static final Set<String> MONEY_ACTIONS =
            Set.of("refund_full", "refund_partial", "refund_partial_goodwill", "refund_duplicate");
    /** Move no money and run even before a human approves. */
    public static final Set<String> SAFE_ACTIONS = Set.of("evidence_pack", "store_refund_instructions",
            "store_cancel_instructions", "reply_only", "ask_clarification");

    private final InMemoryStore store;
    private final AuditService audit;

    public MockBillingService(InMemoryStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    public Map<String, Object> run(String action, CustomerContext ctx, PolicyDecision d, String ticketId) {
        return switch (action) {
            case "cancel" -> cancel(ctx);
            case "refund_full", "refund_partial", "refund_partial_goodwill" -> refund(ctx.lastPayment().id(), d.refundAmount());
            case "refund_duplicate" -> {
                if (ctx.duplicatePaymentId() == null) throw new BillingException("no duplicate payment found");
                yield refund(ctx.duplicatePaymentId(), d.refundAmount());
            }
            case "pause" -> pause(ctx);
            case "extend_access_7d" -> extendAccess(ctx);
            case "evidence_pack" -> evidencePack(ctx, ticketId);
            case "store_refund_instructions", "store_cancel_instructions", "reply_only", "ask_clarification" ->
                    Map.of("noop", true);
            default -> throw new BillingException("unknown action " + action);
        };
    }

    Map<String, Object> refund(String paymentId, double amount) {
        Payment p = store.payment(paymentId);
        if (!p.status.equals("succeeded")) throw new BillingException("payment " + paymentId + " is " + p.status + ", cannot refund");
        if (!p.processor.equals("card")) throw new BillingException("payment " + paymentId + " was made via " + p.processor);
        if (amount <= 0 || amount > p.amount + 1e-9) throw new BillingException("refund " + amount + " outside 0.." + p.amount);
        p.status = "refunded";
        p.refundedAmount = amount;
        p.refundedAt = InMemoryStore.NOW_ISO;
        Customer c = store.customer(p.customerId);
        c.ltvUsd = Math.round((c.ltvUsd - amount) * 100.0) / 100.0;
        return Map.of("payment_id", paymentId, "refunded", amount);
    }

    Map<String, Object> cancel(CustomerContext ctx) {
        Subscription s = store.subscription(ctx.customerId());
        if (s.status.equals("canceled")) return Map.of("subscription_id", s.id, "status", "canceled", "note", "already canceled");
        s.status = "canceled";
        s.canceledAt = InMemoryStore.NOW_ISO;
        return Map.of("subscription_id", s.id, "status", "canceled");
    }

    Map<String, Object> pause(CustomerContext ctx) {
        Subscription s = store.subscription(ctx.customerId());
        s.status = "paused";
        s.pauseUntil = InMemoryStore.NOW.plusDays(60).toString();
        return Map.of("subscription_id", s.id, "status", "paused", "until", s.pauseUntil);
    }

    Map<String, Object> extendAccess(CustomerContext ctx) {
        Subscription s = store.subscription(ctx.customerId());
        s.currentPeriodEnd = OffsetDateTime.parse(s.currentPeriodEnd).plusDays(7).toString();
        return Map.of("subscription_id", s.id, "current_period_end", s.currentPeriodEnd);
    }

    Map<String, Object> evidencePack(CustomerContext ctx, String ticketId) {
        String cid = ctx.customerId();
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("customer", store.customer(cid));
        pack.put("subscription", store.subscription(cid));
        pack.put("payments", store.payments(cid));
        pack.put("usage", store.usage(cid));
        pack.put("tickets", store.tickets.values().stream().filter(t -> t.customerId.equals(cid)).toList());
        pack.put("audit", audit.all().stream()
                .filter(e -> {
                    var t = store.tickets.get((String) e.get("ticket_id"));
                    return t != null && t.customerId.equals(cid);
                }).toList());
        store.evidence.put(ticketId, pack);
        return Map.of("evidence_pack", ticketId, "payments", store.payments(cid).size());
    }
}
