package com.skelar.defuse.service.audit;

import com.skelar.defuse.config.policy.PolicyVersion;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Append-only audit log: every analysis, execution, failure and approval. */
@Service
public class AuditService {
    private final List<Map<String, Object>> events = Collections.synchronizedList(new ArrayList<>());
    private PolicyVersion policyVersion;

    @Autowired(required = false)
    void policyVersion(PolicyVersion policyVersion) {
        this.policyVersion = policyVersion;
    }

    /** Every event names the policy revision in force unless the caller recorded the one it decided under. */
    public Map<String, Object> log(String event, String ticketId, Map<String, Object> fields) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("at", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        e.put("event", event);
        e.put("ticket_id", ticketId);
        e.putAll(fields);
        if (policyVersion != null) e.putIfAbsent("policy_version", policyVersion.get());
        events.add(e);
        return e;
    }

    public List<Map<String, Object>> all() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public List<Map<String, Object>> forTicket(String ticketId) {
        return all().stream().filter(e -> ticketId.equals(e.get("ticket_id"))).toList();
    }

    public void clear() {
        events.clear();
    }
}
