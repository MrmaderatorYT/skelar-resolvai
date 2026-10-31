package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skelar.defuse.config.policy.PolicyProperties;
import com.skelar.defuse.config.policy.PolicyVersion;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.store.InMemoryStore;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Spec G/H, fixture 8: an approval is bound to one exact action, amount and billing state, and it expires. */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewBindingTest {
    @Autowired MockMvc mvc;
    @Autowired InMemoryStore store;
    @Autowired AuditService audit;
    @Autowired TicketPipelineService pipeline;
    @Autowired PolicyVersion policyVersion;

    @BeforeEach void reset() { store.reset(); audit.clear(); }

    private long refundsExecuted() {
        return audit.all().stream().filter(e -> "executed".equals(e.get("event")) && String.valueOf(e.get("action")).startsWith("refund_")).count();
    }

    @Test void analysisCarriesHashExpiryAndPolicyVersion() throws Exception {
        mvc.perform(post("/api/tickets/T11/analyze"))
                .andExpect(jsonPath("$.action_hash").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.review_expires_at").isString())
                .andExpect(jsonPath("$.policy_version").value(policyVersion.get()));
        var a = store.analyses.get("T11");
        assertThat(Instant.parse(a.reviewExpiresAt)).isAfter(Instant.now().plusSeconds(25 * 60));
        assertThat(audit.forTicket("T11").get(0)).containsEntry("policy_version", policyVersion.get()).containsEntry("action_hash", a.actionHash);
        assertThat(audit.all()).isNotEmpty().allSatisfy(e -> assertThat(e).containsEntry("policy_version", policyVersion.get()));
    }

    @Test void policyVersionIsConfiguredOrAContentHash() {
        assertThat(new PolicyVersion(PolicyProperties.defaults(), "demo-v1").get()).isEqualTo("demo-v1");
        assertThat(new PolicyVersion(PolicyProperties.defaults(), "").get()).matches("[0-9a-f]{12}");
        assertThat(policyVersion.get()).isNotBlank();
    }

    @Test void paymentRefundedAfterAnalysisInvalidatesApproval() {
        var a = pipeline.analyze("T11", false);
        assertThat(a.status).isEqualTo("needs_approval");
        var p = store.payment(a.context.lastPayment().id());
        p.status = "refunded"; p.refundedAmount = p.amount;   // refunded elsewhere while the review sat in the queue
        assertThatThrownBy(() -> pipeline.approve("T11", "agent", null, a.analysisId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Billing context changed");
        assertThat(refundsExecuted()).isZero();
        assertThat(audit.forTicket("T11")).anyMatch(e -> "approval_stale".equals(e.get("event")));
    }

    @Test void hashSeesSourceStateTheContextDoesNot() {
        var a = pipeline.analyze("T11", false);
        var trial = store.payments(store.tickets.get("T11").customerId).get(0);
        assertThat(trial.id).isNotEqualTo(a.context.lastPayment().id());
        trial.refundedAmount = trial.amount;   // an older charge changed: no CustomerContext field moves
        assertThatThrownBy(() -> pipeline.approve("T11", "agent", null, a.analysisId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Billing context changed");
        assertThat(store.payment(a.context.lastPayment().id()).status).isEqualTo("succeeded");
    }

    @Test void wrongActionHashIsRefusedAndTheReviewedOneRuns() throws Exception {
        var a = pipeline.analyze("T11", false);
        mvc.perform(post("/api/tickets/T11/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysis_id\":\"" + a.analysisId + "\",\"action_hash\":\"" + "0".repeat(64) + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("Billing context changed")));
        assertThat(store.payment(a.context.lastPayment().id()).status).isEqualTo("succeeded");
        mvc.perform(post("/api/tickets/T11/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysis_id\":\"" + a.analysisId + "\",\"action_hash\":\"" + a.actionHash + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("resolved"));
        assertThat(refundsExecuted()).isEqualTo(1);
    }

    @Test void expiredReviewIsRefused() throws Exception {
        var a = pipeline.analyze("T11", false);
        a.reviewExpiresAt = Instant.now().minusSeconds(1).toString();
        mvc.perform(post("/api/tickets/T11/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysis_id\":\"" + a.analysisId + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("Review expired")));
        assertThat(refundsExecuted()).isZero();
    }

    @Test void rejectNeedsAReason() throws Exception {
        pipeline.analyze("T11", false);
        mvc.perform(post("/api/tickets/T11/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tickets/T11/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"wrong plan\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("escalated"));
        assertThat(audit.forTicket("T11")).anyMatch(e -> "rejected".equals(e.get("event")) && "wrong plan".equals(e.get("reason")));
    }
}
