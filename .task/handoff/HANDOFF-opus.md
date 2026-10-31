# HANDOFF — Opus (`feat-opus`)

Base: `feat` @ `85de9b8` (main merged into feat + offer-parameter getters). Tests: **86 JUnit green**, Python gold-check **44/44**, Codex invariants **38 passed**.

## Done
| Step | What | Files |
|---|---|---|
| 0 | `origin/main` merged into `feat` (Dima's approval binding + injection hardening + our retention); getters `retentionDiscountPercent/Months()`, `retentionPauseMonths()` | `feat` @ 85de9b8 |
| O7 | Labeling guide (Riloff 2013 for sarcasm, OWASP LLM01, escalation research) + 8 few-shot examples, 5 languages, max word-overlap with eval 0.23 | `docs/evidence/LABELING.md`, `backend/src/main/resources/prompts/classifier-fewshot.json` |
| O1 | Evidence for every policy number: source, type, strength A/B/C/H | `docs/evidence/RULES.md` |
| O2 | `defuse-policy.yml` + `PolicyProperties` (records, compact-constructor validation, every value must cite a source); `ReferencePolicyService`, `ContextBuilderService`, `MoneyService` read it; old `defuse.policy.*` @Value keys and `chargeback-fee-usd`/`vamp-fee-usd` removed from `application.yml`; one line added there: `spring.config.import: classpath:defuse-policy.yml` | `config/policy/PolicyProperties.java`, `src/main/resources/defuse-policy.yml` |
| O3 | Rules changed by evidence (below); Python reference + generator + gold + data regenerated | `defuse-data/**`, `src/main/resources/data/**` |
| O4 | Dashboard: save rate, holdout size, discount cost, fees per dispute, VAMP threshold for merchant region | `AnalyticsService`, `Dashboard.tsx`, `types.ts` |
| O5 | Read-only Policy tab; `GET /api/policy`, `GET /api/policy/docs/{RULES,LABELING}` (whitelist) | `PolicyController.java`, `pages/Policy.tsx`, `App.tsx`, `resources/policy-docs/` |
| O6 | 10 slides, demo script, Q&A — every number cites RULES.md or the eval report | `.task/pitch/` |

## Rules changed and why
| Rule | Before | Now | Evidence |
|---|---|---|---|
| Partial refund | flat 50% | pro-rata unused days | EU CRD art. 14(3); CJEU C-641/19 (A) |
| Discount | 25% × 2 months | 30% × 3 months | Churnkey discounts guide (C) |
| Offer cooldown | 365 days | 90 days | Churnkey 3-month cooldown (C) |
| Fees per dispute | $20 + $8 "VAMP" | $15 Stripe + scheme fee from contract (default 0) | Stripe pricing (B); Visa fact sheet has no universal fee |
Gold changes: **T04** refund 14.99 → 9.99; **T10** goodwill 24.99 → 28.01; **T40** scenario: previous offer 150 → 60 days ago (still inside the cooldown), text updated.

## Tests added
`PolicyConfigTest` (YAML = defaults, deprecated statics = config, every rule cites evidence, Java ↔ Python numbers equal, invalid YAML / missing source → context fails, pro-rata T04 = 9.99), `PolicyApiTest` (whitelist incl. `..%2F`, `%2e%2e`; served docs == repo docs), retention assertions updated to 30%.

## Requests to Codex (your files)
1. `ReplyWriterService` and `MockBillingService` still read the **deprecated statics** `ReferencePolicyService.DISCOUNT_PERCENT / DISCOUNT_MONTHS / PAUSE_MONTHS` (now 30/3/2, mirrored and tested). Please switch to the instance getters — then I delete the statics.
2. `docs/features/triage/business-context.md` still says "$8 per dispute" for VAMP — not supported by Visa's public fact sheet; please change to "per-dispute fee from the acquirer contract".
3. Few-shot + labeling guide are ready for your Gemma prompt (§2.2 of your brief).
4. `ReplyWriterService` template wording "we can take 30% off your next 3 months" is fine; keep the "reply CANCEL anytime" line (AB 2863).

## UNVERIFIED / limits
- Churnkey numbers are vendor data without published method (C) — labelled as such everywhere.
- Stripe $15 fee confirmed via two reputable summaries of Stripe pricing, not the Stripe page itself (B).
- CJEU C-641/19 holding on time-proportional calculation is from case notes (Lewis Silkin, LTO), not the judgment text.
- Risk weights, $60 auto-limit, tenure ≥ 2 months, budget 50/30 days, holdout 10% are heuristics (H); measured by eval / holdout, not by external studies.
