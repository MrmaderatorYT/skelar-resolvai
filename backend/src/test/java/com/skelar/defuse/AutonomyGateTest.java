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
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
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
    @Test void pErrIsThePriorOnlyAndConfidenceIsASeparateAbstentionRule() {   // spec E1
        var t = store.tickets.get("T01"); var ctx = contexts.build(t.customerId); var f = LlmClassifierService.fromGold(t);
        var d = policy.decide(f, ctx, 1);
        for (double c : new double[] {1, .97, .90, .85, .50}) {
            var a = policy.assess(f, ctx, d, c);
            assertThat(a.pErr()).as("p_err at confidence " + c).isEqualTo(.02);   // cancel_refund prior, never 1 - confidence
            assertThat(a.expectedLossUsd()).isEqualTo(.02 * 49.99);
        }
        assertThat(policy.assess(f, ctx, d, .97).level()).isEqualTo("AUTO");
        assertThat(policy.assess(f, ctx, d, .96).level()).isEqualTo("ASSIST");
        assertThat(policy.assess(f, ctx, d, .85).level()).isEqualTo("ASSIST");
        assertThat(policy.assess(f, ctx, d, .84).level()).isEqualTo("HUMAN");
    }
    @Test void autonomyRecordsThatTheRiskIsUnvalidated() throws Exception {
        var t = store.tickets.get("T01"); var ctx = contexts.build(t.customerId); var f = LlmClassifierService.fromGold(t);
        var a = policy.assess(f, ctx, policy.decide(f, ctx, 1), 1);
        assertThat(a.riskStatus()).isEqualTo("UNVALIDATED");
        assertThat(a.pBadUpper95()).isNull();
        assertThat(a.nRelevant()).isZero();
        var json = mapper.readTree(mapper.writeValueAsString(a));
        assertThat(json.get("risk_status").asText()).isEqualTo("UNVALIDATED");
        assertThat(json.has("p_bad_upper_95") && json.get("p_bad_upper_95").isNull()).isTrue();
        assertThat(json.get("n_relevant").asInt()).isZero();
        assertThat(json.get("p_err").asDouble()).isEqualTo(.02);
        assertThat(json.has("expected_loss_usd")).isTrue();
    }
    @Test void lowConfidenceReasonOnlyComesFromAutoOrTheGateLikePython() {   // parity with test_policy.py
        var t = store.tickets.get("T11"); var ctx = contexts.build(t.customerId); var f = LlmClassifierService.fromGold(t);
        assertThat(policy.decide(f, ctx, .5).reasons()).containsExactly("above_auto_refund_limit", "autonomy_gate_human", "low_confidence");
        assertThat(policy.decide(f, ctx, .9).reasons()).doesNotContain("low_confidence");
    }
    @Test void ambiguousInformationOnlyDoesNotCreateFinancialAutonomy() {
        var t = store.tickets.get("T01"); var ctx = contexts.build(t.customerId);
        var f = new ClassificationFeatures("unclear", "", false, false, false, false, false, "neutral", "en", 1.0, List.of(), "none");
        var d = policy.decide(f, ctx, 1.0);
        assertThat(d.actions()).containsExactly("ask_clarification");
        assertThat(policy.assess(f, ctx, d, 1.0).level()).isEqualTo("AUTO");
    }
}
