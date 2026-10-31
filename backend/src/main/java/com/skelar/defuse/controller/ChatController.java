package com.skelar.defuse.controller;

import com.skelar.defuse.auth.SessionService;
import com.skelar.defuse.model.domain.Ticket;
import com.skelar.defuse.model.dto.TicketAnalysis;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.llm.LlmSettings;
import com.skelar.defuse.store.InMemoryStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final InMemoryStore store;
    private final TicketPipelineService pipeline;
    private final AuditService audit;
    private final LlmSettings settings;
    public ChatController(InMemoryStore store, TicketPipelineService pipeline, AuditService audit, LlmSettings settings) {
        this.store = store; this.pipeline = pipeline; this.audit = audit; this.settings = settings;
    }
    private String customer(HttpServletRequest request) {
        var session = (SessionService.Session) request.getAttribute("defuse.session");
        if (session == null || !"customer".equals(session.role()) || session.customerId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer session required");
        return session.customerId();
    }
    private Ticket owned(String id, String customerId) {
        Ticket t = store.tickets.get(id);
        if (t == null || !t.customerId.equals(customerId) || !id.startsWith("N"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        return t;
    }
    @GetMapping public List<Map<String, Object>> history(HttpServletRequest request) {
        String customerId = customer(request);
        synchronized (store) { return store.tickets.values().stream().filter(t -> t.id.startsWith("N") && t.customerId.equals(customerId)).map(this::toMessage).toList(); }
    }
    @GetMapping("/suggestions") public List<String> suggestions(HttpServletRequest request) {
        String customerId = customer(request);
        if (!"gold".equals(settings.mode())) return List.of();
        synchronized (store) { return store.tickets.values().stream().filter(t -> !t.id.startsWith("N") && t.customerId.equals(customerId) && t.gold != null).map(t -> t.text).toList(); }
    }
    @PostMapping public Map<String, Object> send(HttpServletRequest request, @Valid @RequestBody Message input) {
        String customerId = customer(request);
        Ticket t;
        synchronized (store) {
            t = store.addTicket(customerId, input.text().trim(), "chat");
            if ("gold".equals(settings.mode())) store.tickets.values().stream()
                    .filter(x -> !x.id.startsWith("N") && x.customerId.equals(customerId) && x.gold != null && x.text.equals(t.text))
                    .findFirst().ifPresent(x -> t.gold = x.gold);
            audit.log("created", t.id, Map.of("customer_id", customerId, "channel", "chat"));
        }
        pipeline.analyze(t.id, false);
        synchronized (store) { return toMessage(t); }
    }
    @PostMapping("/{id}/offer/accept") public Map<String, Object> accept(HttpServletRequest request, @PathVariable String id) {
        synchronized (store) { Ticket t = owned(id, customer(request)); pipeline.acceptOffer(id); return toMessage(t); }
    }
    @PostMapping("/{id}/offer/decline") public Map<String, Object> decline(HttpServletRequest request, @PathVariable String id) {
        synchronized (store) { Ticket t = owned(id, customer(request)); pipeline.declineOffer(id); return toMessage(t); }
    }
    private Map<String, Object> toMessage(Ticket t) {
        TicketAnalysis a = store.analyses.get(t.id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", t.id); result.put("text", t.text); result.put("status", t.status);
        result.put("reply", a == null ? null : a.reply); result.put("mode", a == null || a.decision == null ? null : a.decision.mode());
        result.put("scripted_demo", "gold".equals(settings.mode()) && t.gold != null);
        // cancel-first retention: the renewal is already stopped; this flag only means the one win-back offer is still open
        result.put("offer_pending", a != null && "offer_sent".equals(a.status));
        return result;
    }
    public record Message(@NotBlank @Size(max = 5000) String text) {}
}
