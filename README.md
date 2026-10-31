# 💣 Defuse (skelar-resolvai) — AI support operator that defuses chargebacks

polyTech Code Fest × SKELAR, 31.10.2026. **Java 17+ / Spring Boot 3 + React.**

Subscription apps lose money when a billing ticket ("charged after my trial 🙂") turns into a chargeback: the disputed charge plus issuer and scheme fees, a breach of the Visa VAMP ratio (1.5% for US/CA/EU/AP merchants since April 2026), and a 1★ review. Chatbots answer; Defuse **acts — within a deterministic policy**:

1. understands the **real goal** and catches **sarcasm, hidden aggression and dispute signals** (Claude Haiku 5.5, typed structured output);
2. pulls billing facts (Customer 360) and scores **chargeback risk**;
3. `ReferencePolicyService` picks the action — cancel, refund, pause, store instructions, evidence pack — and the mode: **auto / 1-click approval / escalate**;
4. for cancel requests with a fixable reason, **one retention offer** (30% off for 3 months or a 2-month pause) — only for eligible customers: tenure, no offer in 90 days, no linked-account offer, no refund/chargeback history, no dispute threat, monthly budget, 10% holdout;
5. `ActionExecutorService` executes it (idempotent, payment-state checks, audit), then the reply is written from the actual result (Claude Sonnet 5.5);
6. the dashboard shows chargeback exposure, refund spend, expected fees avoided, and the root causes product should fix.

**The LLM understands and writes. Only code moves money.**

## Run

```bash
# backend — http://localhost:8000
cd backend
./gradlew bootRun                                   # gold mode: replays eval labels, no API key
DEFUSE_LLM_MODE=live ANTHROPIC_API_KEY=... ./gradlew bootRun   # real Claude

# frontend — http://localhost:5173 (proxies /api to :8000)
cd frontend && npm install && npm run dev

# tests (no API calls): JUnit suite, incl. all 45 gold policy decisions
cd backend && ./gradlew test
# live eval gate: 0 unsafe auto-refunds, 0 missed injections
ANTHROPIC_API_KEY=... ./gradlew liveEval
```

## API

| Method | Path | |
|---|---|---|
| GET | `/api/tickets` | queue sorted by priority → risk |
| GET | `/api/tickets/{id}` | ticket + Customer 360 + analysis + audit |
| POST | `/api/tickets/{id}/analyze` | guard → classify → policy → execute → reply |
| POST | `/api/tickets/analyze-all` | analyze every open ticket |
| POST | `/api/tickets/{id}/approve` (`/execute`) | run actions waiting for approval; body must include `analysis_id` returned by analyze |
| POST | `/api/tickets/{id}/reject` | escalate |
| POST | `/api/tickets/{id}/offer/accept` · `/offer/decline` | customer answer to the retention offer |
| POST | `/api/tickets` (`/tickets/simulate`) | live ticket for the demo |
| GET | `/api/analytics/summary` · `/root-causes` · `/dashboard` · `/eval` | KPIs, root causes, eval report |
| POST | `/api/analytics/eval/run` | live classifier on the 45 labelled tickets |
| POST | `/api/demo/reset` | reload seed data |

## Eval (45 labelled tickets, EN/UA/DE/ES/PL)

| Metric | Keyword bot | Defuse (live) |
|---|---|---|
| Sarcastic tickets read as positive | 7/9 | _run `./gradlew liveEval`_ |
| Sarcasm recall | 0% | |
| Missed injections | 3/3 | |
| Unsafe auto-refunds | 2 | **0 (gate)** |
| Correct actions | 62% | |

## Repo

- `backend/` — Spring Boot (`com.skelar.defuse`): `controller/`, `service/{guard,llm,policy,executor,audit,analytics}`, `store/`, `model/`; seed data in `src/main/resources/data/`
- `frontend/` — React: Queue, TicketView, Dashboard, Eval
- `defuse-data/` — Python reference: seed generator (`generate_seed.py`), `reference_policy.py` (source of truth for the 45 gold decisions), `evaluate.py`, keyword baseline, Codex invariant tests
- `docs/` — architecture, security, demo script, `features/<feature>/` (10 files each, SKELAR format; `action-engine/` also has `cross-rail.md`)
- `.task/` — brief, ТЗ, Codex review

All data is synthetic (made-up app "Calmly"). Baseline metrics come from a synthetic human-only month and are labelled as such. Money: a refund is a cost, not a saving — we report gross exposure, refund spend and expected fees avoided separately; fee values are estimates, not Visa-published constants.

### Policy configuration

`backend/src/main/resources/application.yml` contains the configurable refund limits, windows, share, goodwill threshold and minimum confidence under `defuse.policy`. Defaults match the curated 45-case reference ($50, 14 days, two sessions); changing them intentionally changes policy outcomes and can make the reference-label test fail. Invalid ranges fail startup. The health endpoint reports the effective values.

Prompt-injection matches are escalated without sending the ticket to Claude. Low-confidence cancellation waits for approval. Approval includes the reviewed analysis ID and checks the current billing context against that snapshot; a changed context returns HTTP 409 and requires a fresh analysis and review. This remains an in-memory demo without authenticated production billing.


## Demo sign-in

Set `DEFUSE_CUSTOMER_PASSWORD` and `DEFUSE_ADMIN_PASSWORD` in the backend environment before starting the server. No default passwords are provided. Sign in as `customer@calmly.demo` or `admin@calmly.demo`. Optionally set `DEFUSE_CUSTOMER_ID` to a seed customer ID (default `cus_0001`). Sessions are kept in server memory for eight hours; a restart removes them. This is a demonstration, not production authentication.
