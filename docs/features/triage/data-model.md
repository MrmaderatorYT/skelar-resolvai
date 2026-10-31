# Data model

ClassificationFeatures fields are frozen. Parser accepts camelCase aliases from trusted examples and snake_case responses, rejecting conflicting aliases. Booleans must be JSON booleans, confidence numeric/finite/0..1, intent and cancel_reason in fixed enums; evidence must quote literal substrings.

TicketAnalysis retains all existing fields and adds runtime provenance: llm_mode, llm_provider, llm_model, recorded_at. Eval report includes provider, model, generated_at, complete coverage, per-ticket latency and model-only injection recall. Replay cache contains analyses, never gold labels or API keys.
