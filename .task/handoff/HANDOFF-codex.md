# HANDOFF — Codex / feat-codex

Base: `85de9b8` (Merge main into feat + frozen offer getters). Separate clone: `2026-10-polytech-code-fest-skelar-codex`. Main and the other working copies were not changed. Snapshot time: 2026-10-31; final verification/push details will be appended below.

## C1 — Gemma

- Official SDK `com.google.genai:google-genai:1.78.0`, published 2026-10-15. Default documented model `gemma-4-31b-it`; exact account availability is UNVERIFIED. Claude runtime/dependency removed.
- `service/llm/LlmClient`, GemmaClient, settings, strict parser/one repair/fallback, unchanged semantic definitions. Opus LABELING.md + 8 few-shot examples imported verbatim from feat-opus (`f8d35a7`). Separation test passes.
- Replies receive execution results after execution; retention parameters come only from policy getters. Replay records real live analyses and refuses billing mutations. Health adds provider/model; analyses have additive provenance fields.
- Eval uses every gold-labelled seed ticket and pristine seed context, not mutated demo state. Reports add cancel_reason, latency p50/p95, fallback/invalid JSON counters, provider/model/time and raw model injection recall, with keyword baseline alongside.
- API probe + `.github/workflows/gemma-validation.yml`: offline tests always; explicit opt-in and secret for live calls. Probe determines listed model and JSON/schema acceptance. The user explicitly said no local key, secret will be in GitHub Actions.

## API verification (2.1)

| Check | Status | Source / verification |
|---|---|---|
| models.list on the project key | UNVERIFIED — key absent | `backend/scripts/probe_gemma.py`; https://ai.google.dev/api/models |
| Supported hosted Gemma family | Docs confirm Gemma 4; default ID gemma-4-31b-it | https://ai.google.dev/gemma/docs/core/gemma_on_gemini_api |
| Official SDK and minimum release age | 1.78.0; Maven date 2026-10-15; compiles locally | https://central.sonatype.com/artifact/com.google.genai/google-genai/versions |
| System instruction | Gemma4 docs show support; account acceptance UNVERIFIED; initial-user-turn used for compatibility | Google hosted-Gemma page above; https://ai.google.dev/gemma/docs/core/prompt-structure |
| JSON MIME / responseSchema | UNVERIFIED; default disabled until actual probe succeeds | Probe performs real requests, no guessed support |
| RPM / TPM | UNVERIFIED; project/model limits must be checked in AI Studio | https://ai.google.dev/gemini-api/docs/rate-limits |
| Five-call p50 | UNVERIFIED; no key, no requests | Probe records p50 only after five successes |

## Eval comparison

| Metric | Keyword baseline (current seed, 44 tickets) | Gemma |
|---|---|---|
| Sarcasm recall | 0% | UNVERIFIED — not run |
| Sarcastic read as positive | 7/9 | UNVERIFIED |
| Missed injections | 3/3 | UNVERIFIED |
| Unsafe auto refunds | 2 | UNVERIFIED |
| Actions exact match | 61% | UNVERIFIED |
| cancel_reason accuracy / p50 / p95 / fallback count | Baseline resource does not expose these metrics | UNVERIFIED |

Do not put an invented Gemma quality figure on a slide. `./gradlew liveEval` without a key fails; probe without a key exits2 with an UNVERIFIED report. Safety gate is unchanged; an all-fallback API run additionally fails availability rather than looking like a successful live run.

## C2 — SPI

- New `spi/`, `registry/`, `plugins/` packages: 15 individual frozen-action handlers; mock card + app_store/google_play providers; typed read-only JSON sources over the seed store.
- Dispatcher has no action switch. BillingCommand carries ticketId:action key. Executor checks actual payment processor capability; plugin errors escalate without crashing. MockBillingService remains a compatibility facade for existing tests/callers.
- Configuration binds datasources/billing with @ConfigurationProperties/@Validated; unknown types, duplicate registry keys and literal secrets fail startup. JSON classpath/file paths are honored by DataLoader.
- 10 `docs/features/plugins` files document actual scope. No Postgres/Firebase/live payment integration; stretch not started.

## C3 — red-team

24 cases, EN/UK/DE/ES/PL, 12 categories. 18 require guard detection (including 2 oversized); 2 approve JSON attacks, 2 XSS text payloads, 2 bank-threat social-engineering cases exercise their own boundaries. Offline suite verifies guard+policy, size validation, exact JSON text round-trip and unchanged approved actions/amount. Model behavior is UNVERIFIED; tagged LiveRedTeamTest writes per-case guard/model/both-missed evidence when a key exists.

References: OWASP LLM01/LLM05/LLM06 2025 in docs/security.md; Spring configuration and Stripe idempotency in docs/features/plugins/integrations.md. These sources motivate architecture; they do not establish model quality or a production security certification.

## Requests to Opus / integration owner (foreign files left untouched)

1. **App.tsx / API types:** display health `provider` + `model`. Gold badge must say Gold labels. Replay badge: “Replay of live Gemma run at HH:MM” using analysis `recorded_at`; disable Approve/offer controls when `llm_mode=replay` (backend refuses them independently). Existing mode badge alone lacks model/time. App.tsx belongs to Opus.
2. **README.md + docs/architecture.md:** replace Claude runtime text/commands with Gemma, GEMINI_API_KEY and gold/live/replay instructions from docs/features/triage/integrations.md. These files are outside my ownership table, so they were not edited.
3. **GitHub Actions deployment:** add repository secret GEMINI_API_KEY. Set GEMMA_RUN_LIVE=true for an opted-in feature-branch push, or workflow_dispatch after the workflow exists on default. Inject the secret into the deployed Java backend separately; never Vite/React. Deployment host/target is not specified here.
4. **Future non-JSON sources:** ContextBuilderService is Opus-owned and still reads the store. Current configured JSON paths genuinely feed that store; replacing the context source with Postgres later requires wiring CustomerSource/EngagementSource into ContextBuilderService. No claim of a working Postgres connector.
5. **Possible merge conflict:** application.yml LLM/datasources/billing sections are Codex's; preserve Opus's policy/import sections. ReplyWriter/MockBillingOperations now use the three frozen instance getters, so configured offer values flow into execution and replies.

## Demo limitations / UNVERIFIED

- No API key: account model list, JSON/schema support, actual quotas, model latency, live recall, unsafe/missed-injection live metrics and model red-team outcomes are not measured.
- Offline regex success is scoped to 24 curated cases. It does not prove resistance to arbitrary injection; policy/capabilities remain independent.
- Replay is read-only and needs a real live cache; no cache was fabricated or committed. Local gold mode is an independent label replay.
- No deployment target or production identity layer was supplied. This branch provides CI validation, not a claim of deployed production service.
- UI badge and README/architecture updates require the owners' integration above.

## Final local verification before the first push

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/tmp/defuse-codex-gradle ./gradlew test --no-daemon`: **117 tests, 0 failures, 0 errors, 0 skipped**. The 77 existing offline cases remain unchanged; five original test files are byte-identical to base. LiveEvalTest changes only its Claude/Gemma comment.
- Both frozen DTO records are byte-identical to base; no policy/data files were edited. LABELING/few-shot are verbatim imports expressly requested in the brief.
- 24/24 offline red-team dynamic tests pass: 18/18 expected guard hits, plus 6 separate approve/XSS/social boundaries. Live model outcomes remain UNVERIFIED.
- Ten triage docs and ten plugin docs exist; docs JSON and Python probe parse. Actual credential-pattern scan found no Google/Anthropic credential values. Broad grep from the brief also matches pre-existing documentation mentioning scan patterns / env names, so absence of those words is not claimed.
- Keyless API probe was actually run: exit2, explicit UNVERIFIED status, no API calls. No real cache, live eval report or red-team model results were fabricated.
