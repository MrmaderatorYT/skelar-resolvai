package com.skelar.defuse;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.skelar.defuse.service.guard.InjectionGuardService;
import com.skelar.defuse.service.policy.*;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.model.dto.ClassificationFeatures;
import com.skelar.defuse.store.InMemoryStore;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
/** OWASP 2025: LLM01 prompt injection, LLM05 output handling, LLM06 excessive agency.
 * https://genai.owasp.org/llm-top-10/ — offline guard+policy tests do not claim model quality.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RedTeamTest {
    @Autowired ObjectMapper mapper; @Autowired InjectionGuardService guard; @Autowired ReferencePolicyService policy;
    @Autowired ContextBuilderService contexts; @Autowired InMemoryStore store; @Autowired TicketPipelineService pipeline;
    @Autowired MockMvc mvc;
    @TestFactory Stream<DynamicTest> attacks() throws Exception {
        JsonNode attacks=mapper.readTree(getClass().getResourceAsStream("/redteam/attacks.json"));
        assertThat(attacks.size()).isGreaterThanOrEqualTo(24);
        return StreamSupport.stream(attacks.spliterator(),false).map(a->DynamicTest.dynamicTest(a.path("id").asText()+" "+a.path("category").asText(),()->{
            store.reset();String text=a.path("text").asText(),kind=a.path("kind").asText();
            var check=guard.check(text);assertThat(check.flagged()).as(a.path("id").asText()).isEqualTo(a.path("expected_guard").asBoolean());
            var f=ClassificationFeatures.fallback(a.path("language").asText(),"conservative offline test").withInjection(check.flagged());
            var d=policy.decide(f,contexts.build("cus_0010"),f.confidence());
            assertThat(d.refundAmount()).isZero();
            if(check.flagged()) { assertThat(d.mode()).isEqualTo("escalate");assertThat(d.actions()).isEmpty(); }
            if(kind.equals("approve")) {
                var before=pipeline.analyze("T11",false);var actions=List.copyOf(before.decision.actions());double amount=before.decision.refundAmount();
                mvc.perform(post("/api/tickets/T11/approve").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("analysis_id",before.analysisId,"reply_text",a.path("reply_text").asText()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.decision.refund_amount").value(amount));
                assertThat(store.analyses.get("T11").decision.actions()).isEqualTo(actions);
                assertThat(store.payment(before.context.lastPayment().id()).refundedAmount).isEqualTo(amount);
            } else if(kind.equals("oversized")) {
                mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("customer_id","cus_0001","text",text)))).andExpect(status().isBadRequest());
            } else if(kind.equals("xss")) {
                var response=mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("customer_id","cus_0001","text",text))))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();
                String id=mapper.readTree(response.getResponse().getContentAsString()).path("id").asText();
                mvc.perform(get("/api/tickets/"+id)).andExpect(jsonPath("$.ticket.text").value(text));
            } else if(kind.equals("social")) {
                var threatening=new ClassificationFeatures("refund","refund",false,false,false,true,false,"negative",a.path("language").asText(),1,List.of(),"none");
                var outside=policy.decide(threatening,contexts.build("cus_0010"),1);
                assertThat(outside.mode()).isNotEqualTo("auto");
            }
        }));
    }
}
