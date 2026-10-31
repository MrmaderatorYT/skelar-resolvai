package com.skelar.defuse.service.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.skelar.defuse.config.policy.PolicyVersion;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.store.InMemoryStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** Binds a human approval to one exact action (spec G/H): sorted actions, amount in cents, currency, target
 *  payment, processor, policy version and the customer's billing source state. Any change -> new review.
 *  Subscription status is left out on purpose: the cancel that runs before review must not void it. */
@Service
public class ActionHashService {
    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final InMemoryStore store;
    private final PolicyVersion version;

    public ActionHashService(InMemoryStore store, PolicyVersion version) {
        this.store = store;
        this.version = version;
    }

    public String hash(PolicyDecision d, CustomerContext ctx) {
        return PolicyVersion.sha256(canonical(d, ctx));
    }

    String canonical(PolicyDecision d, CustomerContext ctx) {
        String target = targetPayment(d, ctx);
        Payment p = target == null ? null : store.payments.stream().filter(x -> x.id.equals(target)).findFirst().orElse(null);
        Map<String, Object> m = new TreeMap<>();
        m.put("actions", d.actions().stream().sorted().toList());
        m.put("amount_cents", cents(d.refundAmount()));
        m.put("currency", p != null && p.currency != null ? p.currency : "USD");
        m.put("target_payment_id", target);
        m.put("processor", p != null ? p.processor : ctx == null ? null : ctx.billingProcessor());
        m.put("policy_version", version.get());
        m.put("source_state", ctx == null ? null : sourceState(ctx.customerId()));
        try {
            return CANONICAL.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String targetPayment(PolicyDecision d, CustomerContext ctx) {
        if (ctx == null) return null;
        if (d.actions().contains("refund_duplicate")) return ctx.duplicatePaymentId();
        if (d.actions().stream().anyMatch(a -> a.startsWith("refund_"))) return ctx.lastPayment() == null ? null : ctx.lastPayment().id();
        return null;
    }

    public static long cents(double usd) {
        return Double.isFinite(usd) ? Math.round(usd * 100) : -1;
    }

    private Map<String, Object> sourceState(String customerId) {
        List<Map<String, Object>> payments = new ArrayList<>();
        store.payments.stream().filter(p -> p.customerId.equals(customerId)).sorted(Comparator.comparing(p -> p.id)).forEach(p -> {
            Map<String, Object> row = new TreeMap<>();
            row.put("id", p.id);
            row.put("status", p.status);
            row.put("amount_cents", cents(p.amount));
            row.put("refunded_amount_cents", p.refundedAmount == null ? null : cents(p.refundedAmount));
            payments.add(row);
        });
        // cross-rail contracts by id; app/play store status is outside our control, so a change there voids the review
        List<Map<String, Object>> rails = new ArrayList<>();
        store.subscriptions.stream().filter(s -> s.customerId.equals(customerId)).sorted(Comparator.comparing(s -> s.id)).forEach(s -> {
            Map<String, Object> row = new TreeMap<>();
            row.put("id", s.id);
            row.put("processor", s.processor);
            if (!"card".equals(s.processor)) row.put("store_status", s.status);
            rails.add(row);
        });
        Map<String, Object> out = new TreeMap<>();
        out.put("payments", payments);
        out.put("subscriptions", rails);
        return out;
    }
}
