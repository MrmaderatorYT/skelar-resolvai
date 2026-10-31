# Decisions — triage

- **Haiku for classification**: cheap and fast enough to run on every ticket; structured output removes JSON parsing errors.
- **Separate `sentiment` (literal) from `sarcasm`**: lets us show on screen that the literal tone is positive but the meaning is not.
- **Regex guard before the LLM**: injection must be caught even if the model is fooled.
- **Risk score is code, not model output**: explainable weights, testable thresholds.
