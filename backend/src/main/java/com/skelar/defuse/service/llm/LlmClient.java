package com.skelar.defuse.service.llm;

/** Model transport only. It never receives billing tools or executes model output. */
public interface LlmClient {
    String generateJson(String prompt, String schemaHint);
    String generateText(String prompt);
}
