package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;

import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.analytics.EvalService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.service.llm.LlmClassifierService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EvalIsolationTest {
    @Autowired InMemoryStore store;
    @Autowired EvalService eval;
    @Autowired TicketPipelineService pipeline;
    @Autowired AuditService audit;

    @Test
    void demoRefundsDoNotChangeEvaluationContext() {
        store.reset();
        audit.clear();
        try {
            var predictions = new LinkedHashMap<String, ClassificationFeatures>();
            store.tickets.values().stream().filter(t -> t.gold != null)
                    .forEach(t -> predictions.put(t.id, LlmClassifierService.fromGold(t)));
            var before = eval.score(predictions);
            assertThat(before.passedGate()).isTrue();
            assertThat(before.metrics().get("actions_exact_match")).isEqualTo(1.0);

            pipeline.analyze("T01", false);
            assertThat(store.payments(store.tickets.get("T01").customerId))
                    .anyMatch(p -> p.status.equals("refunded"));
            var after = eval.score(predictions);
            assertThat(after.metrics()).isEqualTo(before.metrics());
            assertThat(after.report()).isEqualTo(before.report());
        } finally {
            store.reset();
            audit.clear();
        }
    }
}
