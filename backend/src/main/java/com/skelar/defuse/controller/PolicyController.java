package com.skelar.defuse.controller;

import com.skelar.defuse.config.policy.PolicyProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of the policy and the evidence behind it. Nothing here can change a rule. */
@RestController
@RequestMapping("/api/policy")
public class PolicyController {
    /** Whitelist: only these docs, by exact name. No paths, no traversal. */
    static final Set<String> DOCS = Set.of("RULES", "LABELING");

    private final PolicyProperties props;

    public PolicyController(PolicyProperties props) {
        this.props = props;
    }

    @GetMapping
    public Map<String, Object> rules() {
        return Map.of("rules", props.rules(), "docs", List.copyOf(DOCS));
    }

    @GetMapping("/docs/{name}")
    public Map<String, String> doc(@PathVariable String name) throws IOException {
        if (!DOCS.contains(name)) throw new NoSuchElementException("unknown policy doc");
        try (InputStream in = new ClassPathResource("policy-docs/" + name + ".md").getInputStream()) {
            return Map.of("name", name, "markdown", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
