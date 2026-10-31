package com.skelar.defuse.service.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.service.policy.ContextBuilderService;
import com.skelar.defuse.service.policy.ReferencePolicyService;
import com.skelar.defuse.store.InMemoryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Model reply text survives only when each money or status claim is backed by an executed action. */
@SpringBootTest
class ReplyWriterGuardTest {
    private static final LlmSettings LIVE = new LlmSettings("live", "gemma", "gemma-4-31b-it", "GEMINI_API_KEY", 15000, 2, false, false);

    @Autowired InMemoryStore store;
    @Autowired ContextBuilderService contexts;
    @Autowired ReferencePolicyService policy;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void reset() {
        store.reset();
    }

    /** Stub transport: returns the given text as if Gemma had written it. */
    private ReplyWriterService writer(String modelText) {
        LlmClient stub = new LlmClient() {
            @Override public String generateJson(String prompt, String schemaHint) { throw new UnsupportedOperationException(); }
            @Override public String generateText(String prompt) { return modelText; }
        };
        return new ReplyWriterService(stub, mapper, LIVE, policy);
    }

    private PolicyDecision decision(List<String> actions) {
        return new PolicyDecision(actions, "auto", 0, 0, "low", "P3", new ArrayList<>());
    }

    private String write(String modelText, List<String> actions) {
        var t = store.tickets.get("T01");
        List<Map<String, Object>> executed = actions.stream().<Map<String, Object>>map(a -> Map.<String, Object>of("action", a)).toList();
        return writer(modelText).write(t, LlmClassifierService.fromGold(t), contexts.build(t.customerId), decision(actions),
                store.customer(t.customerId).name, executed);
    }

    private String template(List<String> actions) {
        var t = store.tickets.get("T01");
        return writer("").template(store.customer(t.customerId).name, decision(actions), contexts.build(t.customerId));
    }

    @Test
    void unbackedRefundClaimFallsBackToTheTemplate() {
        String text = "Good news, we have refunded $30.00 to your card.";
        assertThat(write(text, List.of("reply_only"))).isEqualTo(template(List.of("reply_only")));
    }

    @Test
    void unbackedStatusWordFallsBackToTheTemplate() {
        String text = "Your subscription has been cancelled and you will not be charged again.";
        assertThat(write(text, List.of("reply_only"))).isEqualTo(template(List.of("reply_only")));
    }

    @Test
    void unbackedAmountInAnotherCurrencyFallsBackToTheTemplate() {
        assertThat(write("You will get €12 back soon.", List.of("reply_only"))).isEqualTo(template(List.of("reply_only")));
    }

    @Test
    void cleanTextIsKeptAsWritten() {
        String text = "Thanks for your patience, we are looking into it.";
        assertThat(write(text, List.of("reply_only"))).isEqualTo(text);
    }

    @Test
    void claimsAreCheckedAgainstCompletedActionsOnly() {
        assertThat(ReplyWriterService.unbackedClaim("Your refund of $30.00 has been refunded.", List.of("refund_partial"))).isFalse();
        assertThat(ReplyWriterService.unbackedClaim("Your refund of $30.00 has been refunded.", List.of("reply_only"))).isTrue();
        assertThat(ReplyWriterService.unbackedClaim("Your renewal is paused.", List.of("cancel"))).isTrue();
        assertThat(ReplyWriterService.unbackedClaim("Your renewal is paused.", List.of("pause"))).isFalse();
        assertThat(ReplyWriterService.unbackedClaim("We are looking into it, thanks.", List.of())).isFalse();
    }
}
