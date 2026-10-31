package com.skelar.defuse;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {
    @Autowired MockMvc mvc;
    @Autowired InMemoryStore store;
    @Autowired AuditService audit;

    @BeforeEach
    void reset() {
        store.reset();
        audit.clear();
    }

    @Test
    void analyzeReturnsSnakeCaseContract() throws Exception {
        mvc.perform(post("/api/tickets/T01/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"))
                .andExpect(jsonPath("$.features.true_goal").exists())
                .andExpect(jsonPath("$.decision.refund_amount").value(49.99))
                .andExpect(jsonPath("$.context.refunds_last_180d").value(0))
                .andExpect(jsonPath("$.money.p_dispute").exists())
                .andExpect(jsonPath("$.money.dispute_fee_scenario").isNumber())
                .andExpect(jsonPath("$.money", not(hasKey("expected_fees_avoided"))))
                .andExpect(jsonPath("$.action_hash").isString())
                .andExpect(jsonPath("$.policy_version").isString())
                .andExpect(jsonPath("$.review_expires_at").isString());
    }

    @Test
    void goldLabelsNeverLeak() throws Exception {
        mvc.perform(get("/api/tickets/T01")).andExpect(jsonPath("$.ticket", not(hasKey("gold"))));
        mvc.perform(get("/api/tickets")).andExpect(jsonPath("$[0]", not(hasKey("gold"))));
    }

    @Test
    void queueIsSortedAndDashboardCounts() throws Exception {
        mvc.perform(post("/api/tickets/analyze-all")).andExpect(status().isOk());
        mvc.perform(get("/api/tickets")).andExpect(jsonPath("$[0].priority").value("P1"))
                .andExpect(jsonPath("$[44].priority").value("P4"));
        mvc.perform(get("/api/analytics/summary"))
                .andExpect(jsonPath("$.analyzed").value(45))
                .andExpect(jsonPath("$.injections_blocked").value(3))
                .andExpect(jsonPath("$.dispute_fee_scenario_usd").isNumber())
                .andExpect(jsonPath("$.dispute_scenario_count").isNumber())
                .andExpect(jsonPath("$", not(hasKey("expected_fees_avoided_usd"))));
        mvc.perform(get("/api/analytics/diagnostics"))
                .andExpect(jsonPath("$.routes.auto").isNumber()).andExpect(jsonPath("$.routes.review").isNumber())
                .andExpect(jsonPath("$.routes.investigate").isNumber()).andExpect(jsonPath("$.review_reasons").isMap())
                .andExpect(jsonPath("$.blocked_money_actions").isNumber()).andExpect(jsonPath("$.risk_status").value("UNVALIDATED"));
    }

    @Test
    void approveAndSimulateEndpoints() throws Exception {
        mvc.perform(post("/api/tickets/T12/analyze")).andExpect(jsonPath("$.status").value("needs_approval"));
        mvc.perform(post("/api/tickets/T12/execute").contentType(MediaType.APPLICATION_JSON).content("{\"analysis_id\":\"" + store.analyses.get("T12").analysisId + "\"}"))
                .andExpect(jsonPath("$.status").value("resolved"));
        mvc.perform(post("/api/tickets/simulate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customer_id\":\"cus_0001\",\"text\":\"hi\"}"))
                .andExpect(jsonPath("$.id").value("N01"));
        mvc.perform(get("/api/tickets/NOPE")).andExpect(status().isNotFound());
    }
}
