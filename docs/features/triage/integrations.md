# Gemma integration evidence — 2026-10-31

| Check | Result | Evidence / reproduction |
|---|---|---|
| Hosted instruction-tuned models | Official docs list Gemma 4 `gemma-4-31b-it` and `gemma-4-26b-a4b-it`; account availability UNVERIFIED | https://ai.google.dev/gemma/docs/core/gemma_on_gemini_api |
| Default model | `gemma-4-31b-it`, override `DEFUSE_LLM_MODEL`; CI probe selects from the actual models.list | `GemmaClient`, `scripts/probe_gemma.py`; https://ai.google.dev/api/models |
| Java SDK | Official `com.google.genai:google-genai:1.78.0`; Maven publication 2026-10-15, 16 days before this review | https://central.sonatype.com/artifact/com.google.genai/google-genai/versions ; SDK compiles and offline suite passes |
| System instruction | Gemma 4 docs support it; older Gemma formatting docs do not. This implementation always uses the initial user turn | https://ai.google.dev/gemma/docs/core/prompt-structure ; Gemma-on-API page above. Account probe UNVERIFIED |
| JSON MIME / responseSchema | UNVERIFIED for this account; both default false. Strict Jackson parsing and one repair work without native schema support | Probe checks both separately; successful CI probe sets the two feature flags |
| RPM / TPM | UNVERIFIED; quotas are per project/model, not an intrinsic property of one key | https://ai.google.dev/gemini-api/docs/rate-limits ; read actual limits in AI Studio, do not infer them from five calls |
| p50 on five requests | UNVERIFIED, key unavailable | `python3 scripts/probe_gemma.py` writes per-call status/latency and p50 only after five successful requests |
| Live classifier quality | UNVERIFIED, no live run | `GEMINI_API_KEY=... ./gradlew liveEval`; full gold-labelled seed coverage, guarded and model-only injection metrics |

The user confirmed the key is not available locally and will be a GitHub Actions secret. No live API requests were made. Probe without a key exits 2 with an UNVERIFIED report, never an invented model result.

## Run and configure

```bash
cd backend
./gradlew test                                # gold, no key/network to a model
DEFUSE_LLM_MODE=live ./gradlew bootRun         # backend must receive GEMINI_API_KEY via environment
python3 scripts/probe_gemma.py                 # models.list, actual feature acceptance, five-call p50
GEMINI_API_KEY=... ./gradlew liveEval          # sequential classifier + live red-team, consumes quota
```

`GEMINI_API_KEY` is read explicitly by the backend and never serialized, logged or built into frontend assets. The SDK's environment-variable defaults are not relied on. SDK client is lazy; gold/replay need no key. Timeout is 15,000 ms per HTTP attempt; at most two retries on 429/5xx, with the SDK's backoff. A classification gets at most one separate schema-repair attempt. Transport failure immediately produces confidence 0 and human review.

Sources for SDK construction, request configuration and retry semantics: https://github.com/googleapis/java-genai ; https://googleapis.github.io/java-genai/javadoc/com/google/genai/types/HttpOptions.html . Actual live latency and account/model behavior remain UNVERIFIED until CI runs.

## GitHub Actions

Workflow `.github/workflows/gemma-validation.yml` always runs offline tests. Live calls require explicit opt-in plus `GEMINI_API_KEY` repository secret. On the feature branch, set repository variable `GEMMA_RUN_LIVE=true` and push; after the workflow exists on the default branch, use workflow_dispatch with `run_live=true`. Secret use: https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets . Missing requested secret fails the live step. No opt-in writes an explicit UNVERIFIED artifact.

The probe exports only a listed model ID and verified schema flags through GITHUB_ENV. Artifacts contain probe/eval/red-team reports and test reports, never credentials. GitHub Secrets are available to Actions jobs; deployment must separately inject the secret into the running Java backend. Static frontend hosting alone cannot run Spring Boot. Deployment target is UNVERIFIED / owned by the main integrator.

## Replay

After a real live analysis run, `POST /api/demo/record` writes `backend/replay/analyses.json` when the app runs from backend. `DEFUSE_LLM_MODE=replay` reuses it with no model or billing calls. Cache entries bind ticket ID, customer ID and SHA-256 of text. Missing/mismatched entry fails with HTTP409; it never substitutes gold or calls live. Approve/offer mutations are refused in replay. The cache is local runtime evidence, not a checked-in fake live run.

`GET /api/health` adds provider/model; `GET /api/demo/llm` provides runtime mode. Analyses add `llm_mode`, `llm_provider`, `llm_model`, `recorded_at`. Opus owns App.tsx: render “Replay of live Gemma run at HH:MM” from these fields; do not label gold as live/replay-of-live. This UI integration is requested in HANDOFF.
