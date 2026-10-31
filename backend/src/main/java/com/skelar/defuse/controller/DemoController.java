package com.skelar.defuse.controller;

import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.llm.LlmClassifierService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoController {
    private final InMemoryStore store;
    private final AuditService audit;
    private final LlmClassifierService classifier;

    public DemoController(InMemoryStore store, AuditService audit, LlmClassifierService classifier) {
        this.store = store;
        this.audit = audit;
        this.classifier = classifier;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "llm_mode", classifier.mode(), "policy", store.policy,
                "auto_refund_limit_usd", ReferencePolicyService.AUTO_REFUND_LIMIT_USD);
    }

    @PostMapping("/demo/reset")
    public Map<String, Object> reset() {
        store.reset();
        audit.clear();
        return Map.of("ok", true, "tickets", store.tickets.size());
    }
}
