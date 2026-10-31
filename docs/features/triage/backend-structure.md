# Backend structure

- `service/llm/LlmClient`: JSON/text transport contract.
- `GemmaClient`: lazy official SDK client, bounded HTTP attempts; no tools.
- `LlmClassifierService`: JSON-quoted ticket, unchanged labeling semantics, exact required types/enums, one repair then fallback.
- `Prompts`: loads Opus-authored LABELING and few-shot resources unchanged.
- `ReplyWriterService`: execution-aware reply payload and configured offer getters; exceptions use templates.
- `ReplayService`: atomic record, hash-bound read-only restore; no gold-to-live substitution.
- `EvalService`: fresh seed contexts, complete gold set, cancel_reason, latency and fallback diagnostics, baseline alongside live metadata.
