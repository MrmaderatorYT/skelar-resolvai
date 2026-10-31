package com.skelar.defuse.store;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.stereotype.Component;

/** In-memory store seeded from data/*.json. Mock billing mutates it; reset() reloads the seed. */
@Component
public class InMemoryStore {
    /** Pinned clock: refund windows and risk scores are reproducible. Same as reference_policy.NOW. */
    public static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 31, 12, 0, 0, 0, ZoneOffset.UTC);
    public static final String NOW_ISO = NOW.toString();

    private final DataLoader loader;

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

    public InMemoryStore(DataLoader loader) {
        this.loader = loader;
        reset();
    }

    public synchronized void reset() {
        customers = loader.load("customers", new TypeReference<>() { });
        subscriptions = loader.load("subscriptions", new TypeReference<>() { });
        payments = loader.load("payments", new TypeReference<>() { });
        usage = loader.load("usage", new TypeReference<>() { });
        history = loader.load("tickets_history", new TypeReference<>() { });
        policy = loader.load("policy", new TypeReference<>() { });
        tickets = new LinkedHashMap<>();
        for (Ticket t : loader.load("tickets_eval", new TypeReference<List<Ticket>>() { })) {
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
        return subscriptions.stream().filter(s -> s.customerId.equals(customerId)).findFirst().orElseThrow();
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
        tickets.put(t.id, t);
        return t;
    }
}
