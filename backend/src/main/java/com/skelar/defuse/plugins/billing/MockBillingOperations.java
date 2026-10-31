package com.skelar.defuse.plugins.billing;
import com.skelar.defuse.service.executor.BillingException;

import com.skelar.defuse.model.domain.Customer;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.domain.Subscription;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Mock billing provider: each action mutates the store the way Stripe/Solidgate would. */
@Service
public class MockBillingOperations {
    public static final Set<String> MONEY_ACTIONS =
            Set.of("refund_full", "refund_partial", "refund_partial_goodwill", "refund_duplicate");
    /** Move no money and run even before a human approves. */
    public static final Set<String> SAFE_ACTIONS = Set.of("evidence_pack", "store_refund_instructions",
            "store_cancel_instructions", "reply_only", "ask_clarification");

    private final InMemoryStore store;
    private final AuditService audit;
    private final ReferencePolicyService policy;

    public MockBillingOperations(InMemoryStore store, AuditService audit, ReferencePolicyService policy) {
        this.store = store;
        this.audit = audit;
        this.policy = policy;
    }

    public Map<String, Object> refund(String paymentId, double amount) {
        Payment p = store.payment(paymentId);
        if (!p.status.equals("succeeded")) throw new BillingException("payment " + paymentId + " is " + p.status + ", cannot refund");
        if (!p.processor.equals("card")) throw new BillingException("payment " + paymentId + " was made via " + p.processor);
        if (!Double.isFinite(amount) || amount <= 0 || amount > p.amount + 1e-9) throw new BillingException("refund " + amount + " outside 0.." + p.amount);
        p.status = "refunded";
        p.refundedAmount = amount;
        p.refundedAt = InMemoryStore.NOW_ISO;
        Customer c = store.customer(p.customerId);
        c.ltvUsd = Math.round((c.ltvUsd - amount) * 100.0) / 100.0;
        return Map.of("payment_id", paymentId, "refunded", amount);
    }

    public Map<String, Object> cancel(CustomerContext ctx) {
        Subscription s = store.subscription(ctx.customerId());
        if (s.status.equals("canceled")) return Map.of("subscription_id", s.id, "status", "canceled", "note", "already canceled");
        s.status = "canceled";
        s.canceledAt = InMemoryStore.NOW_ISO;
        return Map.of("subscription_id", s.id, "status", "canceled");
    }

    public Map<String, Object> pause(CustomerContext ctx) {
        Subscription s = store.subscription(ctx.customerId());
        if (s.status.equals("canceled")) throw new BillingException("subscription " + s.id + " is canceled");
        s.status = "paused";
        s.pauseUntil = InMemoryStore.NOW.plusMonths(policy.retentionPauseMonths()).toString();
        return Map.of("subscription_id", s.id, "status", "paused", "until", s.pauseUntil);
    }

    /** Sending an offer moves no money, but it is what the 12-month cooldown counts. */
    public Map<String, Object> recordOffer(CustomerContext ctx, String action) {
        Customer c = store.customer(ctx.customerId());
        c.offers12m += 1;
        c.lastOfferAt = InMemoryStore.NOW_ISO;
        return action.equals("offer_discount")
                ? Map.of("offer", "discount", "percent", policy.retentionDiscountPercent(), "months", policy.retentionDiscountMonths())
                : Map.of("offer", "pause", "months", policy.retentionPauseMonths());
    }

    public Map<String, Object> applyDiscount(CustomerContext ctx) {
        Subscription s = store.subscription(ctx.customerId());
        if (!s.status.equals("active")) throw new BillingException("subscription " + s.id + " is " + s.status);
        s.discountPercent = policy.retentionDiscountPercent();
        s.discountUntil = InMemoryStore.NOW.plusMonths(policy.retentionDiscountMonths()).toString();
        return Map.of("subscription_id", s.id, "discount_percent", s.discountPercent, "until", s.discountUntil);
    }

    public Map<String, Object> extendAccess(CustomerContext ctx) {
        Subscription s = store.subscription(ctx.customerId());
        s.currentPeriodEnd = OffsetDateTime.parse(s.currentPeriodEnd).plusDays(7).toString();
        return Map.of("subscription_id", s.id, "current_period_end", s.currentPeriodEnd);
    }

    public Map<String, Object> evidencePack(CustomerContext ctx, String ticketId) {
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
