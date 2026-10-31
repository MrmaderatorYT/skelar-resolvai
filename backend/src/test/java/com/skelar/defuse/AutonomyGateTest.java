package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.service.llm.LlmClassifierService;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AutonomyGateTest {
    @Autowired InMemoryStore store;
    @Autowired ContextBuilderService contexts;
    @Autowired ReferencePolicyService policy;
    @BeforeEach void reset() { store.reset(); }
    @Test void confidenceControlsWhetherRefundNeedsHuman() {
        var t = store.tickets.get("T01"); var ctx = contexts.build(t.customerId); var f = LlmClassifierService.fromGold(t);
        assertThat(policy.decide(f, ctx, 1).mode()).isEqualTo("auto");
        assertThat(policy.decide(f, ctx, .97).mode()).isEqualTo("auto");
        assertThat(policy.decide(f, ctx, .85).mode()).isEqualTo("suggest");
        var assist = policy.decide(f, ctx, .90);
        assertThat(assist.mode()).isEqualTo("suggest");
        assertThat(policy.assess(f, ctx, assist, .90).level()).isEqualTo("ASSIST");
        var human = policy.decide(f, ctx, .50);
        assertThat(human.mode()).isEqualTo("escalate");
        assertThat(policy.assess(f, ctx, human, .50).level()).isEqualTo("HUMAN");
    }
    @Test void ambiguousInformationOnlyDoesNotCreateFinancialAutonomy() {
        var t = store.tickets.get("T01"); var ctx = contexts.build(t.customerId);
        var f = new ClassificationFeatures("unclear", "", false, false, false, false, false, "neutral", "en", 1.0, List.of(), "none");
        var d = policy.decide(f, ctx, 1.0);
        assertThat(d.actions()).containsExactly("ask_clarification");
        assertThat(policy.assess(f, ctx, d, 1.0).level()).isEqualTo("AUTO");
    }
}
