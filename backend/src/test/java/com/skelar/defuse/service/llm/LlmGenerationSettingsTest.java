package com.skelar.defuse.service.llm;
import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
class LlmGenerationSettingsTest {
    private static LlmSettings settings(org.springframework.core.env.Environment env) {
        return new LlmSettings("gold", "gemma", "gemma-4-31b-it", "GEMINI_API_KEY", 15000, 2, false, false, env);
    }
    @Test void defaultsAreGreedyAndDeterministic() {
        var s = new LlmSettings("gold", "gemma", "gemma-4-31b-it", "GEMINI_API_KEY", 15000, 2, false, false);
        assertThat(s.classifier()).isEqualTo(new LlmSettings.Generation(0.0f, 1.0f, 1, 7, 1024, "minimal"));
        assertThat(s.reply()).isEqualTo(new LlmSettings.Generation(0.0f, 1.0f, 1, 7, 400, "minimal"));
        assertThat(settings(new MockEnvironment())).isEqualTo(s);
    }
    @Test void applicationYmlStatesTheSameDefaults() throws Exception {
        var env = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")))
            env.getPropertySources().addLast(source);
        var s = settings(env);
        assertThat(s.classifier()).isEqualTo(LlmSettings.Generation.CLASSIFIER);
        assertThat(s.reply()).isEqualTo(LlmSettings.Generation.REPLY);
    }
    @Test void perCallSettingsAreConfigurableAndValidated() {
        var env = new MockEnvironment().withProperty("defuse.llm.reply.thinking-level", "low").withProperty("defuse.llm.classifier.seed", "11");
        var s = settings(env);
        assertThat(s.reply().thinkingLevel()).isEqualTo("low"); assertThat(s.classifier().seed()).isEqualTo(11);
        assertThat(s.classifier().maxOutputTokens()).isEqualTo(1024);
        assertThatThrownBy(() -> settings(new MockEnvironment().withProperty("defuse.llm.classifier.top-k", "0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings(new MockEnvironment().withProperty("defuse.llm.reply.thinking-level", "max")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void gemmaRequestUsesTheConfiguredSettings() {
        var c = GemmaClient.config(LlmSettings.Generation.CLASSIFIER).build();
        assertThat(c.temperature()).isEqualTo(Optional.of(0.0f)); assertThat(c.topP()).isEqualTo(Optional.of(1.0f));
        assertThat(c.topK()).isEqualTo(Optional.of(1.0f)); assertThat(c.seed()).isEqualTo(Optional.of(7));
        assertThat(c.maxOutputTokens()).isEqualTo(Optional.of(1024));
        assertThat(GemmaClient.config(LlmSettings.Generation.REPLY).build().maxOutputTokens()).isEqualTo(Optional.of(400));
        assertThat(c.thinkingConfig().orElseThrow().thinkingLevel()).isPresent();
    }
    @Test void liveModeRefusesToRunWithoutTheLabelingGuide() {
        var few = new ByteArrayInputStream("[]".getBytes());
        assertThatThrownBy(() -> Prompts.classifierPrompt(null, few)).isInstanceOf(IllegalStateException.class).hasMessageContaining("LABELING.md");
        assertThatThrownBy(() -> Prompts.classifierPrompt(new ByteArrayInputStream("guide".getBytes()), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("classifier-fewshot.json");
        assertThat(Prompts.classifierPrompt()).contains("Authoritative labeling definitions").contains("hidden_aggression")
                .contains("missing_information").contains("Never infer facts");
    }
}
