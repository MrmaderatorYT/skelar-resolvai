# Decisions — action-engine

- **The LLM has no write tools.** Money moves only through `decide()` + executor.
- **Idempotency key per ticket and action**: re-analysis never refunds twice.
- **Cancel runs immediately even in suggest mode**: it moves no money; only refunds wait for approval.
- **In-memory store** instead of a DB: seeded from JSON, one-click reset for demos.
