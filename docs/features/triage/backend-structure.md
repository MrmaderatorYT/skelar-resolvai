# Backend structure — triage

- `service/guard/InjectionGuardService.java` — regex list, `check(text) -> InjectionCheck(flagged, patterns)`
- `service/llm/Prompts.java` — `CLASSIFIER_SYSTEM`
- `service/llm/LlmClassifierService.java` — `classify()`, `classifyLive()`, `fromGold()`, safe `ClassificationFeatures.fallback()` (confidence 0)
- `model/dto/ClassificationFeatures.java, Evidence.java` — `Features`, `Evidence`
- `service/policy/ReferencePolicyService.java (+ ContextBuilderService.java)` — `riskScore()`, `priority()`
