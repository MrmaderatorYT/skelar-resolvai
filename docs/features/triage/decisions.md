# Decisions

- Use official Google Gen AI SDK 1.78.0 (stable, published at least two weeks before the task); account/model verification stays UNVERIFIED without key.
- Keep semantic definitions and eight examples from Opus unchanged; test normalized equality and word Jaccard≤0.8 versus every eval ticket.
- Use initial user-turn instructions for Gemma compatibility. Native JSON/schema flags are opt-in after the account probe succeeds.
- Missing/coerced fields never become primitive defaults. One repair only; transport failure gives confidence0.
- Replay is explicitly read-only. It cannot approve pending money or re-execute a recorded refund.
- Guard/model detection is reported separately; combined safety does not masquerade as model-only recall.
