# End-to-end flow — triage

1. Ticket arrives (seed queue or POST /api/tickets).
2. `InjectionGuardService` runs regexes on the raw text (before any LLM).
3. `LlmClassifierService` sends the text inside `<ticket>` tags to Claude Haiku 5.5 with structured output (`ClassificationFeatures`).
4. Injection = regex OR model flag.
5. `ReferencePolicyService.riskScore()` computes score and band from the features and the customer context.
6. `priority()` sets P1–P4; the queue is sorted by priority, then risk score.
