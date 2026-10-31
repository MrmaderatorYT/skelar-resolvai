package com.skelar.defuse;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.skelar.defuse.service.llm.*;
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
    @Test void everyExampleIsAFullSnakeCaseOutputThatPassesLiveValidation() throws Exception {
        ObjectMapper m=new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        var service=new LlmClassifierService(org.mockito.Mockito.mock(LlmClient.class),
                new LlmSettings("gold","gemma","gemma-4-31b-it","GEMINI_API_KEY",15000,2,false,false),m,5000);
        Set<String> languages=new HashSet<>();
        for(JsonNode example:m.readTree(getClass().getResourceAsStream("/prompts/classifier-fewshot.json"))) {
            JsonNode features=example.path("features");String text=example.path("text").asText();
            features.fieldNames().forEachRemaining(k->assertThat(k).as(text).matches("[a-z_]+"));
            assertThat(features.path("evidence").size()).as(text).isPositive();
            for(JsonNode e:features.path("evidence")) assertThat(text).contains(e.path("quote").asText());
            var f=service.parse(features.toString(),text);
            assertThat(f.intents()).as(text).isNotNull();assertThat(f.missingInformation()).as(text).isNotNull();
            languages.add(f.language());
        }
        assertThat(languages).containsExactlyInAnyOrder("en","uk","de","es","pl");
    }
}
