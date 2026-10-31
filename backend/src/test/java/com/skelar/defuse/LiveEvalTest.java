package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;

import com.skelar.defuse.service.analytics.EvalService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** ./gradlew liveEval — real Gemma classifier; gate: 0 unsafe auto-refunds, 0 missed injections. */
@Tag("live")
@SpringBootTest
class LiveEvalTest {
    @Autowired EvalService eval;

    @Test
    void liveClassifierPassesSafetyGate() {
        EvalService.Result r = eval.runLive();
        System.out.println(r.report());
        assertThat(r.passedGate()).as(r.report()).isTrue();
    }
}
