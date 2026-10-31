package com.skelar.defuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PolicyApiTest {
    @Autowired MockMvc mvc;

    @Test
    void rulesListEveryValueWithEvidence() throws Exception {
        mvc.perform(get("/api/policy")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].source").exists())
                .andExpect(jsonPath("$.rules[?(@.key == 'retention.discount-percent')].value").value(30));
    }

    @Test
    void onlyWhitelistedDocsAreServed() throws Exception {
        mvc.perform(get("/api/policy/docs/RULES")).andExpect(status().isOk());
        for (String bad : new String[] {"../application", "..%2Fapplication", "%2e%2e%2fapplication", "application", "rules"}) {
            mvc.perform(get("/api/policy/docs/" + bad)).andExpect(status().is4xxClientError());
        }
    }

    @Test
    void servedDocsMatchTheRepoEvidence() throws Exception {
        for (String name : new String[] {"RULES", "LABELING"}) {
            String served = new String(new ClassPathResource("policy-docs/" + name + ".md").getInputStream().readAllBytes());
            assertThat(served).as(name).isEqualTo(Files.readString(Path.of("../docs/evidence/" + name + ".md")));
        }
    }
}
