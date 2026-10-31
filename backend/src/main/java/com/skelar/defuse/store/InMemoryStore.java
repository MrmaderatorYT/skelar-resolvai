package com.skelar.defuse.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.skelar.defuse.data.DemoDatabase;
import com.skelar.defuse.model.domain.Customer;
import com.skelar.defuse.model.domain.HistoryTicket;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.domain.Subscription;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.domain.Usage;
import com.skelar.defuse.model.dto.TicketAnalysis;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/** In-memory store seeded from data/*.json. Mock billing mutates it; reset() reloads the seed. */
@Component
public class InMemoryStore {
    /** Pinned clock: refund windows and risk scores are reproducible. Same as reference_policy.NOW. */
    public static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 31, 12, 0, 0, 0, ZoneOffset.UTC);
    public static final String NOW_ISO = NOW.toString();

    private final DataLoader loader;
    private final DemoDatabase database;

    public List<Customer> customers;
    public List<Subscription> subscriptions;
    public List<Payment> payments;
    public List<Usage> usage;
    public List<HistoryTicket> history;
    public Map<String, Object> policy;
    public Map<String, Ticket> tickets;
    public Map<String, TicketAnalysis> analyses;
    public Map<String, Map<String, Object>> executed;   // "ticketId:action" -> result (idempotency)
    public Map<String, Map<String, Object>> evidence;
    private int newTicketSeq;

    public InMemoryStore(DataLoader loader, DemoDatabase database) {
        this.loader = loader; this.database = database;
        database.initialize();
        load();
    }

    /** Explicit demo reset: erases and reseeds the database, then reloads the mock ledger from it. */
    public synchronized void reset() {
        database.reset();
        load();
    }

    private void load() {
        customers = database.customers();
        subscriptions = database.subscriptions();
        payments = database.payments();
        usage = database.usage();
        history = loader.load("tickets_history", new TypeReference<>() { });
        policy = loader.load("policy", new TypeReference<>() { });
        tickets = new LinkedHashMap<>();
        for (Ticket t : database.tickets()) {
            t.status = "open";
            tickets.put(t.id, t);
        }
        analyses = new LinkedHashMap<>();
        executed = new LinkedHashMap<>();
        evidence = new LinkedHashMap<>();
        newTicketSeq = 0;
    }

    public Customer customer(String id) {
        return customers.stream().filter(c -> c.id.equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("customer " + id + " not found"));
    }

    public Subscription subscription(String customerId) {
        List<Subscription> owned = subscriptions.stream().filter(s -> s.customerId.equals(customerId)).toList();
        if (owned.size() == 1) return owned.get(0);
        if (owned.isEmpty()) throw new NoSuchElementException("no_subscription: customer " + customerId);
        List<Subscription> cards = owned.stream().filter(s -> "card".equals(s.processor)).toList();
        List<Subscription> stores = owned.stream().filter(s -> "app_store".equals(s.processor) && "canceled".equals(s.status)).toList();
        var links = customer(customerId).linkedSubscriptions;
        if (cards.size() == 1 && stores.size() == 1 && owned.size() == 2 && links != null && links.size() == 2
                && links.contains(cards.get(0).id) && links.contains(stores.get(0).id)
                && payments.stream().anyMatch(p -> customerId.equals(p.customerId) && cards.get(0).id.equals(p.subscriptionId) && "card".equals(p.processor))) return cards.get(0);
        throw new IllegalStateException("ambiguous_subscriptions: human review required");
    }

    public Usage usage(String customerId) {
        return usage.stream().filter(u -> u.customerId.equals(customerId)).findFirst().orElseThrow();
    }

    public List<Payment> payments(String customerId) {
        List<Payment> out = new ArrayList<>(payments.stream().filter(p -> p.customerId.equals(customerId)).toList());
        out.sort(Comparator.comparing(p -> p.createdAt));
        return out;
    }

    public Payment payment(String id) {
        return payments.stream().filter(p -> p.id.equals(id)).findFirst().orElseThrow();
    }

    public synchronized Ticket addTicket(String customerId, String text, String channel) {
        Customer c = customer(customerId);
        Ticket t = new Ticket();
        t.id = "N%02d".formatted(++newTicketSeq);
        t.customerId = customerId;
        t.channel = channel == null ? "chat" : channel;
        t.language = c.language;
        t.createdAt = NOW_ISO;
        t.text = text;
        database.insertTicket(t);
        tickets.put(t.id, t);
        return t;
    }
    /** Snapshot mutable objects so a failed SQL write cannot leave the mock ledger half-mutated. */
    public synchronized MutationSnapshot snapshot(String customerId) {
        var rows = new java.util.IdentityHashMap<Object, Map<java.lang.reflect.Field, Object>>();
        var objects = new ArrayList<Object>(); objects.add(customer(customerId));
        subscriptions.stream().filter(s -> customerId.equals(s.customerId)).forEach(objects::add);
        payments.stream().filter(p -> customerId.equals(p.customerId)).forEach(objects::add);
        try {
            for (Object object : objects) {
                Map<java.lang.reflect.Field, Object> fields = new java.util.LinkedHashMap<>();
                for (var field : object.getClass().getFields()) fields.put(field, field.get(object));
                rows.put(object, fields);
            }
        } catch (IllegalAccessException e) { throw new IllegalStateException("Cannot snapshot mock state", e); }
        return new MutationSnapshot(rows);
    }
    public record MutationSnapshot(Map<Object, Map<java.lang.reflect.Field, Object>> rows) {
        public void restore() {
            try { for (var row : rows.entrySet()) for (var field : row.getValue().entrySet()) field.getKey().set(row.getKey(), field.getValue()); }
            catch (IllegalAccessException e) { throw new IllegalStateException("Cannot restore mock state", e); }
        }
    }
    public synchronized void persistAction(String ticketId, String action, com.skelar.defuse.model.dto.CustomerContext ctx, double refundAmount) {
        if (!java.util.Set.of("cancel", "pause", "apply_discount", "extend_access_7d", "offer_discount", "offer_pause", "refund_full", "refund_partial", "refund_partial_goodwill", "refund_duplicate").contains(action)) return;
        String paymentId = action.equals("refund_duplicate") ? ctx.duplicatePaymentId() : ctx.lastPayment().id();
        database.persistAction(ticketId, action, customer(ctx.customerId()), subscription(ctx.customerId()),
                action.startsWith("refund_") && paymentId != null ? payment(paymentId) : null, refundAmount);
    }

}
