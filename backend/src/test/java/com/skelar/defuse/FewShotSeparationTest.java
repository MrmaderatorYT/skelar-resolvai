package com.skelar.defuse;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.text.Normalizer;
import java.util.*;
import org.junit.jupiter.api.Test;
class FewShotSeparationTest {
    private Set<String> words(String s) { return new HashSet<>(Arrays.asList(Normalizer.normalize(s,Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim().split(" +"))); }
    @Test void opusExamplesAreNotEvalOrNearDuplicates() throws Exception {
        ObjectMapper m=new ObjectMapper();
        var few=getClass().getResourceAsStream("/prompts/classifier-fewshot.json");assertThat(few).as("Opus few-shot file required").isNotNull();
        var eval=getClass().getResourceAsStream("/data/tickets_eval.json");
        JsonNode examples=m.readTree(few),tickets=m.readTree(eval);assertThat(examples.size()).isGreaterThanOrEqualTo(8);
        for(JsonNode example:examples) for(JsonNode ticket:tickets) {
            Set<String> a=words(example.path("text").asText()),b=words(ticket.path("text").asText());
            Set<String> intersection=new HashSet<>(a);intersection.retainAll(b);Set<String> union=new HashSet<>(a);union.addAll(b);
            assertThat(a).isNotEqualTo(b);
            assertThat((double)intersection.size()/Math.max(1,union.size())).as(ticket.path("id").asText()).isLessThanOrEqualTo(.8);
        }
    }
}
