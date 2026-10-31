package com.skelar.defuse.controller;

import com.skelar.defuse.model.domain.Customer;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.Requests;
import com.skelar.defuse.model.dto.TicketAnalysis;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.store.InMemoryStore;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TicketController {
    private static final Map<String, Integer> PRIO = Map.of("P1", 0, "P2", 1, "P3", 2, "P4", 3);

    private final InMemoryStore store;
    private final TicketPipelineService pipeline;
    private final ContextBuilderService contexts;
    private final AuditService audit;

    public TicketController(InMemoryStore store, TicketPipelineService pipeline, ContextBuilderService contexts,
                            AuditService audit) {
        this.store = store;
        this.pipeline = pipeline;
        this.contexts = contexts;
        this.audit = audit;
    }

    private Ticket ticket(String id) {
        Ticket t = store.tickets.get(id);
        if (t == null) throw new NoSuchElementException("ticket " + id + " not found");
        return t;
    }

    private Map<String, Object> row(Ticket t) {
        TicketAnalysis a = store.analyses.get(t.id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", t.id);
        r.put("customer_id", t.customerId);
        r.put("customer_name", store.customer(t.customerId).name);
        r.put("channel", t.channel);
        r.put("language", t.language);
        r.put("created_at", t.createdAt);
        r.put("text", t.text);
        r.put("text_preview", t.text.length() > 90 ? t.text.substring(0, 90) : t.text);
        r.put("status", t.status);
        r.put("priority", a == null ? null : a.decision.priority());
        r.put("risk_band", a == null ? null : a.decision.riskBand());
        r.put("risk_score", a == null ? null : a.decision.riskScore());
        r.put("mode", a == null ? null : a.decision.mode());
        List<String> signals = new ArrayList<>();
        if (a != null) {
            var f = a.features;
            if (f.sarcasm()) signals.add("sarcasm");
            if (f.hiddenAggression()) signals.add("hidden_aggression");
            if (f.explicitAggression()) signals.add("explicit_aggression");
            if (f.disputeSignal()) signals.add("dispute_signal");
            if (f.injection()) signals.add("injection");
        }
        r.put("signals", signals);
        return r;
    }

    /** Queue: priority, then risk score (desc), then age. */
    @GetMapping("/tickets")
    public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
        List<Ticket> snapshot;
        synchronized (store) { snapshot = List.copyOf(store.tickets.values()); }
        return snapshot.stream()
                .filter(t -> status == null || status.equals(t.status))
                .map(this::row)
                .sorted(Comparator.<Map<String, Object>>comparingInt(r -> r.get("priority") == null ? 9 : PRIO.get((String) r.get("priority")))
                        .thenComparing(r -> -(r.get("risk_score") == null ? 0.0 : (Double) r.get("risk_score")))
                        .thenComparing(r -> (String) r.get("created_at")))
                .toList();
    }

    @GetMapping("/tickets/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        synchronized (store) { return detail(id); }
    }

    /** Reads money state under the store lock, so a refund rolled back by a failed SQL write is never shown. */
    private Map<String, Object> detail(String id) {
        Ticket t = ticket(id);
        String cid = t.customerId;
        Map<String, Object> customer = new LinkedHashMap<>();
        Customer c = store.customer(cid);
        customer.put("id", c.id);
        customer.put("name", c.name);
        customer.put("country", c.country);
        customer.put("language", c.language);
        customer.put("platform", c.platform);
        customer.put("ltv_usd", c.ltvUsd);
        customer.put("contacts_last_30d", c.contactsLast30d);
        customer.put("acquisition_channel", c.acquisitionChannel);
        customer.put("funnel", c.funnel);
        customer.put("quiz_goal", c.quizGoal);
        customer.put("offers_12m", c.offers12m);
        // ambiguous contracts (fixture 12): show no single subscription/context rather than a guessed one or a 409
        boolean ambiguous = pipeline.ambiguousSubscriptions(cid) != null;
        customer.put("subscription", ambiguous ? null : orNull(() -> store.subscription(cid)));
        customer.put("linked_subscriptions", store.subscriptions.stream().filter(s -> cid.equals(s.customerId)).toList());
        customer.put("usage", store.usage(cid));
        customer.put("payments", store.payments(cid));
        customer.put("context", ambiguous ? null : orNull(() -> contexts.build(cid)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ticket", t);
        out.put("customer", customer);
        out.put("analysis", store.analyses.get(id));
        out.put("audit", audit.forTicket(id));
        out.put("evidence", store.evidence.get(id));
        return out;
    }

    private static Object orNull(java.util.function.Supplier<Object> read) {
        try { return read.get(); }
        catch (IllegalStateException e) {   // "...human review required": the analysis explains it, the page still loads
            if (e.getMessage() == null || !e.getMessage().contains("human review required")) throw e;
            return null;
        }
    }

    @PostMapping({"/tickets", "/tickets/simulate", "/demo/simulate"})
    public Ticket create(@Valid @RequestBody Requests.NewTicket body) {
        Ticket t = store.addTicket(body.customerId(), body.text(), body.channel());
        audit.log("created", t.id, Map.of("customer_id", body.customerId()));
        return t;
    }

    @PostMapping("/tickets/analyze-all")
    public List<String> analyzeAll() {
        List<String> open = store.tickets.values().stream().filter(t -> t.status.equals("open")).map(t -> t.id).toList();
        open.forEach(id -> pipeline.analyze(id, false));
        return open;
    }

    @PostMapping("/tickets/{id}/analyze")
    public TicketAnalysis analyze(@PathVariable String id, @RequestParam(defaultValue = "false") boolean force) {
        ticket(id);
        return pipeline.analyze(id, force);
    }

    @GetMapping("/customers")
    public List<Map<String, Object>> customers() {
        return store.customers.stream().map(c -> Map.<String, Object>of("id", c.id, "name", c.name,
                "platform", c.platform, "language", c.language, "plan", store.subscription(c.id).plan)).toList();
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> auditLog(@RequestParam(required = false) String ticketId) {
        return ticketId == null ? audit.all() : audit.forTicket(ticketId);
    }
}
