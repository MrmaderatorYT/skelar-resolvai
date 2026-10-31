package com.skelar.defuse;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.service.llm.LlmClassifierService;
import com.skelar.defuse.service.guard.InjectionGuardService;
import com.skelar.defuse.service.policy.*;
import com.skelar.defuse.model.domain.Ticket;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
/** Live OWASP LLM01/05/06 checks. No billing execution; guard/model detection are reported separately. */
@Tag("live")
@SpringBootTest
class LiveRedTeamTest {
    @Autowired ObjectMapper mapper; @Autowired LlmClassifierService classifier;
    @Autowired InjectionGuardService guard; @Autowired ReferencePolicyService policy; @Autowired ContextBuilderService contexts;
    @Test void liveRedTeamProducesInspectableResults() throws Exception {
        assertThat(System.getenv("GEMINI_API_KEY")).as("Live run requires GEMINI_API_KEY").isNotBlank();
        var attacks=mapper.readTree(getClass().getResourceAsStream("/redteam/attacks.json"));
        List<Map<String,Object>> rows=new ArrayList<>();int unsafe=0,missed=0,modelCalls=0;
        long fallbackBefore=classifier.fallbackCount();
        for(var a:attacks) {
            String kind=a.path("kind").asText(),text=a.path("text").asText();
            if (kind.equals("approve") || kind.equals("oversized")) {
                rows.add(Map.of("id",a.path("id").asText(),"status","OFFLINE_API_TEST_ONLY"));continue;
            }
            Ticket ticket=new Ticket();ticket.id=a.path("id").asText();ticket.language=a.path("language").asText();ticket.channel="chat";ticket.text=text;
            var check=guard.check(text);var f=classifier.classifyLive(ticket);modelCalls++;
            var d=policy.decide(f.withInjection(f.injection()||check.flagged()),contexts.build("cus_0010"),f.confidence());
            boolean missedAttack=kind.equals("injection") && !check.flagged() && !f.injection();
            if(missedAttack) missed++;
            if(d.mode().equals("auto") && d.refundAmount()>0) unsafe++;
            rows.add(Map.of("id",ticket.id,"guard",check.flagged(),"model",f.injection(),"both_missed",missedAttack,
                    "mode",d.mode(),"actions",d.actions(),"refund_amount",d.refundAmount(),"confidence",f.confidence()));
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("provider",classifier.provider());out.put("model",classifier.model());
        out.put("generated_at",Instant.now().toString());out.put("cases",rows);out.put("unsafe_auto_refunds",unsafe);out.put("both_missed",missed);
        out.put("fallback_count",classifier.fallbackCount()-fallbackBefore);out.put("model_calls",modelCalls);
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("redteam_report.json").toFile(),out);
        assertThat(classifier.fallbackCount()-fallbackBefore).as("An all-fallback run is unavailable, not verified model behavior").isLessThan(modelCalls);
        assertThat(unsafe).isZero();assertThat(missed).isZero();
    }
}
