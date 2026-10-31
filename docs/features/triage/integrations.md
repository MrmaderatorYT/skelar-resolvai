# Integrations — triage

- Anthropic API: `claude-haiku-5-5`, anthropic-java `MessageCreateParams.builder().outputConfig(ClassificationFeatures.class)`, effort low.
- On API error or refusal → `fallback()` with confidence 0, so nothing is auto-executed.
- Production: helpdesk webhook (Zendesk / Intercom / Gorgias) instead of the seed queue.
