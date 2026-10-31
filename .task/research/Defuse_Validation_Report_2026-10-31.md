# Defuse 2.0: market validation, buyer selection, and decision architecture

**Decision report | Evidence cut-off: 31 October 2026 | Status: REVIEW DRAFT**

**Audience:** Defuse product team, SKELAR hackathon judges, and eventual technical implementers.  
**Read together with:** `DEFUSE-PROJECT-CONTEXT.md`, `DEFUSE-STATE-FOR-IDEA-RESEARCH.md`, and `Defuse_market_validation_2026-10-31.md`.  
**Evidence labels:** **A** = official law, regulatory/API documentation, company product terms or first-party product pricing; **B** = independent report with methodology or corroborated industry reporting; **C** = unverified vendor, employer, or case-study claim; **H** = Defuse hypothesis/design proposal; **UNVERIFIED** = evidence absent. An App Store price is a publicly listed SKU, **not** every subscriber's effective checkout price. Neither downloads nor positive unit economics prove company-wide net profit.

## Executive summary: the decision

1. **Target one business problem, not an entire generic support category:** complicated billing support for growing B2C web-to-app subscription businesses, beginning in wellness, habit-building, and adjacent lifestyle/education. Position to SKELAR as **an AI support agent with a billing-resolution superpower**; implement the actual product as a narrower **billing resolution layer** that can later connect to established support tools.
2. **Best specific buyer hypothesis:** **Leaply (SKELAR)** has an unusually strong public fit: a young subscription venture, self-reported early positive unit economics, 200k+ users, ~60+ employees, an identifiable payments/operations function, and a job opening describing a single payments provider that it plans to diversify [S1, S2, S3]. That does **not** prove net profitability, urgent demand for Defuse, willingness to pay, or an active Apple-plus-web duplicate subscription problem. **PawChamp** is a particularly strong source of evidence for distinct web/store refunds [S4].
3. **The most defensible product workflow** is: authenticated request -> multi-intent/tone extraction -> independently read payment and entitlement facts -> identify the exact transaction and the customer's goal -> check policy and compliant retention eligibility -> compute evidence-backed action risk -> AUTO / REVIEW / INVESTIGATE -> provider-confirmed execution -> accurate customer response -> business insight. For explicit cancellation, do **not** obstruct or delay cancellation with retention offers.
4. **Critical design correction:** the LLM's `confidence=0.85` is **not** an empirically valid `15% chance of error`. A 3% or 15% operational limit needs observed, held-out validation by action type and a conservative uncertainty bound. Until validated, money-moving actions can be demonstration-only with mock billing; production default is human approval.
5. **Judging story:** one case, one clear root cause, one controlled action. A user says, "I canceled on my iPhone, but you charged me again. Stop all remaining renewals and refund the last charge, or I'll contact my bank." Defuse finds two verified subscriptions on separate billing rails, stops the verified and explicitly authorized web renewal, and prepares a specific requested refund for human approval with references, cost, reasoning, audit, and a product-issue classification. The agent does not claim to prevent an actual chargeback before that outcome has been measured.

## 1. Stakeholder advice: tested against the current product and outside evidence

| Advice heard | Validation | Decision for Defuse |
|---|---|---|
| Build for a specific, growing small business that already has revenue but cannot justify a bespoke support automation team | **Directionally sound (H)**; public revenue and profitability at venture level usually not disclosed | Target **post-PMF / scaling** B2C subscriptions with existing billing tickets, at least one integration budget owner and insufficient bespoke automation capacity. Exclude tiny, no-revenue startups, and very large firms with complete in-house stacks. Do not equate small company with guaranteed willingness to buy. |
| Broad customer support, but a retention differentiator | **Only partly defensible.** Intercom, Zendesk, Decagon, and Churnkey already support portions of actions/retention [S16-S19] | Explain the agent in broad terms, but specialize its *decision quality* in billing, account/transaction reconciliation, controlled concessions and human review. Do not claim retention alone is a moat. |
| Ask why customers leave, then offer discounts/pause before refunds | **Conditional, not universal.** Google Play survey shows cost and lack of use are common cancellation reasons (B) [S11], but wanting a refund may indicate a charge was unauthorized or service failed | Offer a **single optional** concession only when it matches a genuine need and is allowed by policy. Never make the user complete a survey or accept a retention pitch to cancel. Never substitute a discount for an owed refund. |
| AI should evaluate sentiment, sarcasm, intent and error probability | **Intent and danger signals relevant; self-confidence not reliable as error probability** | Structured extraction with quoted evidence; statistically measured risk by action/segment. Interpret sarcasm as a clue, not a legal or financial ground to deny rights. |
| Route by risk %, amount, and financial damage if wrong | **Excellent architecture concept; the quoted 3%, 15%, $50, $200, $8 are illustrative H values** | Add hard policy/safety gates first, then calibrated `p_bad_upper`, loss-if-wrong, action limit, and human review. Keep money-out distinct from incremental *mistake* cost. |
| Agent completes 95% of work, human clicks approve | **Feasible, not unique**: competitors already provide approval workflows [S16-S17] | Differentiate with provider-specific evidence, precise transaction identity, reasons and versioned policy. An approval must be bound to exact arguments and revalidated on execution. |
| Basic analytics, segmentation, alerts | **Essential but not a differentiator alone** [S18] | Include verified resolutions, risk blocks, root causes by payment rail and checkout, review reasons, and repeat contacts; never invent chargebacks saved. |
| Multi-channel customer support, mainly email in US | **Plausible, company-dependent; 'US is all email' is UNVERIFIED** | Use a channel-agnostic TicketEnvelope and stage-2 inbound/outbound email adapters. Do not build email transport for today's one-case demo. |
| UI quality must be good enough to show a prospective customer tomorrow | **A useful MVP standard (H)** | Create a believable customer entry point plus an approval inbox, visible before/after payment state, and clear error handling. |
| Concentrate on one case | **Highly compelling for the hackathon** | Prioritize cross-rail cancellation/payment mismatch and optional small separate retention test. |

**Important change in US regulation:** the FTC's broad 2024 'click-to-cancel' amended rule was vacated in July 2025 [S15]. That does **not** eliminate FTC Act/ROSCA obligations or California's automatic-renewal law. Do not cite the vacated federal rule as active law. California cancellation, consumer-law and payment-network requirements must be verified for each merchant/jurisdiction.

## 2. Verified market problem: subscriptions are difficult to support correctly

RevenueCat's **State of Subscription Apps 2026** covers over 115,000 apps and over $16B in tracked revenue [S11]. Within its dataset, the *median refunded-subscription share during the first billing period* is **4.2% for AI apps vs 3.5% for non-AI apps**. Across apps the report reports a **4.7%** median for Productivity. Refund rate is **not** the share of customer-support tickets and **not** card dispute rate. Approximately **32.2% of Google Play cancellations vs 15.2% of App Store cancellations** in the observed data were attributable to billing errors; this is platform cancellation reasons, not a rate of bank chargebacks. Reasons captured in Google Play exit survey include cost and low usage, but cannot automatically justify a retention strategy. Source: [S11] (B).

The relevant differentiator is that one person's product identity may map to multiple **billing contracts**, **payment providers**, and **entitlements**, while the user describes the problem in human terms. A generic response, "Cancel via Settings," may be factually correct for the Apple contract yet fail to identify another web purchase. A public Liven/BBB case in the prior research describes that pattern; it is an individual report (C), not evidence of its frequency [S20].

**Financial exposure:** Stripe confirms that a received card dispute reverses the relevant payment and carries dispute fees; a refund before a dispute can help but does not guarantee that the consumer's bank will not subsequently open a dispute [S12]. Visa VAMP considers fraud and disputes over settled card-not-present transactions; the published excessive merchant threshold from 1 April 2026 is **1.5% for US/CA/EU/AP** (with monthly minimum counts) and **2.2% for CEMEA** under additional conditions [S13]. Different acquiring setups are not interchangeable. This makes accurate dispute-risk triage meaningful, but **no measured Defuse reduction in dispute counts exists**.

## 3. Companies to test as potential buyers (real prices, honest profit status)

**Interpretation:** these are **specific market candidates / comparable products**, not signed customers. 'Potential buyer' is an inference based on subscription business model and company signals; their actual Zendesk/Intercom/PSP architecture, profitability and purchasing decisions require interviews.

| Product / ecosystem | Public price or purchasable SKU (US) | Scale / maturity evidence | How Defuse might help; limit | Assessment |
|---|---|---|---|---|
| **Leaply / SKELAR** | **Price UNVERIFIED** from public static pages; official policy lists possible **4-, 12-, 24-week** terms with variable checkout offers [S5] | Launch **June 2024**; claims **200k+ users**, **60+ staff**, positive **unit economics**, plus hiring in payments engineering [S1-S3] (C) | Strongest ICP fit: subscription, checkout and operations. Payments vacancy says **one processor handling cards/Apple Pay/PayPal**; this alone **does not imply Apple App Store billing or two rails**. | **Priority 1; company profit UNVERIFIED** |
| **PawChamp / SKELAR** | App Store: **$9.99/week**; **$54.99 or $79.99/year** SKUs [S6] (A) | App listing ~16k US ratings; '1M+ dog parents' is developer marketing (C) | Official membership policy explicitly covers **web** plus **Apple/Google purchases** and describes different refund handling [S4]. High-fit billing policy complexity. | **Priority 2; company profit UNVERIFIED** |
| **Dressly / SKELAR** | App Store: **$11.99/week**, **$34.99/month**, **$79.99/year** [S7] (A) | Founder claims **800k+ downloads** and 20x growth during 2025-26 [S8] (C) | AI fashion subscriptions; payment-rail, offer, upsell, usage mismatch. Growing enough to have CX operations; direct ticket volumes unknown. | **Priority 3; company profit UNVERIFIED** |
| **RiseGuide / SKELAR** | App Store: **$9.99/week**, **$29.99/month**, **$69.99/year** [S9] (A) | ~19k US App Store ratings; paid product (A for displayed SKUs, not profit) | Similar education/habit engagement problems; ownership of payments not verified. | **Priority 4; profit UNVERIFIED** |
| **Liven / SKELAR** | Official Liven site: **from $7.99/week**, **$49.99/month**, **$59.99/year**, varying by channel/market [S10] (A) | Established major product; more likely to have mature internal systems; specific case of dual subscriptions [S20] (C) | **Excellent design partner/example**, not obviously an early-stage small buyer. | **Benchmark, not primary ICP; profit UNVERIFIED** |
| **PlantIn / Genesis ecosystem** | App Store: **$6.99/week**, **$29.99/year** among options [S21] (A) | Existing app with paid renewals; support volume unknown | Comparable light subscription model, smaller than major unicorn products; web-to-app mix unknown. | **Secondary outbound; profit UNVERIFIED** |
| **Impulse / Genesis ecosystem** | App Store: **$9.99/week**, promo **$29.49/year** among options [S22] (A) | Developer claims >100M downloads; likely mature (C) | Relevant subscription behaviors but probably more internal engineering capacity. | **Benchmark, not startup-first** |
| **Promova / Genesis/Boosters** | App Store: **$9.99/week**, **$19.99/month**, **$83.99/year** among SKUs [S23] (A) | **October 2026 Forbes** reports downsizing from 186 to 12 and return to Boosters [S24] (B reporting a company interview) | Potential efficiency pain, but weaker 'profitable, growing startup' match; no claim Promova itself profitable. | **Not a preferred first buyer** |

**False-match warning (material):** an App Store entry named **'Liven'** with id `6737596528` is attributed to **7Logic LLC** and must not be used as the SKELAR product. The relevant Liven AI Life Coach is associated with `theliven.com` and App Store id `6450840109` [S10, S25].

### Why the Leaply evidence stands out

The posted Senior Engineer (Payment & Ops) vacancy names processing, checkout, subscriptions, and plans to build an alternative processing route to complement one current provider [S3]. This is much closer to real buyer pain than making assumptions from ratings. An earlier CTO/engineering job claims early **positive unit economics**, not audited net profit [S2]. An operations/monetization hiring notice explicitly spans support and online reputation [S26]. All of these are company-reported descriptions, not independent proof of demand.

**Buyer roles:** Head of Support/CX and Payments & Operations lead as champions; Head of Product as beneficiary; founder/COO or finance lead as budget owner in a smaller company; security/engineering as integration gatekeeper. Specific reporting lines are unverified. An early-stage startup without budget for paid support is **not** the target; neither is an enterprise with a complete in-house dispute and AI platform.

**Proposed measurable qualification (H, not industry facts):** recurring billing revenue + at least 100-300 billing/cancellation issues per month; one owner able to approve a pilot; at least one reachable billing data source; measured time-per-case > basic FAQ; willingness to provide anonymized historical tickets. These are interview screening heuristics, **not claims** about any company listed.

### Subscription price is not annual recurring revenue

Do **not** multiply weekly App Store prices by a download/user count to infer revenue, because the available counts are often lifetime downloads, ratings or total users, not currently paying subscriptions. Discounts, local pricing, payment-channel mix, cancellations and tax also matter. A company can have good unit economics and still be net loss-making. **Net profit status for the individual candidates: UNVERIFIED.**

## 4. Single buyer persona and jobs-to-be-done

**Primary buyer persona (H):** Head of CX / Support & Billing at a 10-100-person growth-stage consumer subscription company selling in North America. Company has web-based acquisition and more than one type of purchase; support manually checks transaction provenance, refunds, cancellation eligibility and plans in several tools. The team pays for a helpdesk but not custom automation. Cannot trust an LLM to move money unsupervised; needs measurable safeguards and rapid setup.

**Specific day-in-the-life pain:**
1. A customer writes an angry message, often without an order ID or with a misleading recollection of where they purchased.
2. The operator checks customer email, payment history, subscription source, trial dates and prior refunds, potentially across multiple systems.
3. The operator must distinguish a real unauthorized/duplicate charge from a valid but misunderstood renewal, a pending refund, or a separate web subscription.
4. The correct outcome may be cancel, receipt explanation, a discretionary concession, full/partial refund, provider routing or fraud investigation.
5. Today the resolution is slow or poorly evidenced, and the next operator often has to reconstruct it again.

**Job statement:** "For each subscription complaint, tell me *which exact payment and contractual entitlement* it refers to, what actions are allowed and financially prudent, and present a verified resolution without introducing unauthorized charges, refunds or retention friction."

**Who is NOT an ideal customer:** a business that deliberately monetizes cancellation obstruction; company with no payment integrations or enough ticket volume; merchant expecting Defuse to issue Apple Store refunds directly; a firm unwilling to share minimum legal identity/contractual evidence.

## 5. Product promise and one narrow demonstration

**Customer-facing:** "Tell us what went wrong. We check your subscriptions and resolve what we safely can; complicated payments get reviewed with all the evidence already prepared."

**Business-facing:** "Defuse resolves subscription billing incidents with source-backed decisions, merchant-owned refund rules and human approval where a mistake is costly. It learns recurring payment problems as structured product signals."

**Primary demo (one story):**
- User: "I canceled the app subscription on my iPhone, but you've charged my card again. Stop all remaining renewals and refund the last charge. I'd rather resolve this here than with my bank."
- Gemma extracts: `stop_future_billing`, `identify_latest_charge`, `consider_refund`, `bank_dispute_warning`, negative tone; provides **exact text spans**, not invented facts.
- Context service finds *authorized* contracts: App Store subscription A, canceled; web subscription B, active; web invoice/payment P confirmed paid; refund not yet initiated; no open dispute. Never infer same account solely from a card fingerprint.
- The system reports the mismatch; explicit web cancellation (where owned/authorized) is carried out first. The policy engine offers a refundable amount only under the merchant's chosen rules or returns human review if legality/eligibility is unclear.
- Agent produces an evidence bundle containing subscription ids, provider, payment id, event times, policy version, proposed action, financial exposure and why an employee must approve.
- Human approves or rejects **exactly the financial action**; executor checks current payment state and idempotency again, then records provider response.
- Only after provider confirmation does the agent say "Your web renewal has stopped; your $X refund was processed / has been referred for review" with accurate status and expected next steps.
- Dashboard records `cross_rail_cancel_mismatch` as a source-linked cause. Never claims a bank dispute was prevented merely because the ticket closed.

**Separate 20-second optional retention branch (not the primary story):** authenticated user says, "I like it but I cannot afford it." If the user has not made a direct, final cancellation/refund request and the plan permits, offer **one** discount or pause with clear new price, duration and full-price renewal terms. If declined, cancel as requested. No blocking question, no forced waiting, no repeated offer. The current fixed 30%-for-3-months / 2-month pause rules are **merchant-specific hypotheses**, not universal optima.

## 6. Five strongest enhancements (scores 1-5 are H judgments, not measurements)

The six criteria are: Operational Impact (OI), Innovation (IN), Use of AI (AI), Reasoning & Context (RC), Technical Execution (TE), Presentation (PD). Scored for **potential quality of demonstration**, not achieved quality.

| Idea | Evidence of need | OI | IN | AI | RC | TE | PD | Total | Decision |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| **Cross-rail payment/entitlement reconciliation** | Apple/Google/Stripe APIs [S27-S29]; PawChamp policy [S4]; isolated Liven case [S20] | 5 | 5 | 5 | 5 | 4 | 5 | **29/30** | **Primary demo** |
| **Verified risk-gated action + exact human approval** | OWASP [S30], Fin HITL [S17] | 5 | 4 | 5 | 5 | 4 | 5 | **28/30** | **Primary demo** |
| **Refund pending / duplicate refund prevention** | Stripe status/dispute rules [S12, S31] | 5 | 4 | 4 | 5 | 4 | 5 | **27/30** | Small supplementary check |
| **Product root-cause incident bundle** | Payments job lists investigation [S3]; Decagon already does VoC [S18] | 4 | 4 | 5 | 4 | 3 | 4 | **24/30** | Dashboard minimal, rich later |
| **Consent-aware save offer tied to actual cancellation reason** | RevenueCat cancellation reasons [S11], existing churn vendors [S19] | 4 | 3 | 4 | 4 | 4 | 3 | **22/30** | Keep present module; not the centerpiece |

**Competitive correction:** Intercom Fin Procedures/Tasks, Zendesk, and Decagon already perform API actions, refunds and complex escalations [S16-S18]. A new dashboard or generic AI retention classifier is not defensible on its own. Your plausible differentiation is **the exact billing identity and transaction + deterministic merchant policy + financial approval evidence + post-incident signals**, contingent on implementation quality. No customer comparisons proving superiority exist yet.

## 7. The proposed autonomy model, separated into three *different* measurements

The second stakeholder suggested error probability, money amount and cost of a wrong action. **This is a good basis for a decision framework, but these are not interchangeable.** The earlier `p_err = max(empirical error, 1 - model_confidence)` is not statistically sound unless model confidence has been independently calibrated.

### Gate 0: hard facts (must pass before any financial action)

- Authenticated principal authorized to view and act on this **merchant's** account; cross-account correlations are clues, not identity proof.
- Exact payment matched to exact contract, provider, currency, timestamps and ownership.
- Action capability available on that **provider**; do not assume every store supports the same API.
- No open/ambiguous dispute, refund already processed, duplicate action, concurrent processing or stale data.
- Merchant's policy allows action; applicable law/contract and required consent are established. If unclear: **REVIEW**, not 'auto deny'.
- Required audit and side-effect reconciliation operational. On failure: fail closed.

### Measurement 1: probability that this action is wrong

For production, estimate `p_bad(action | intent, payment rail, source completeness, model version, language, and scenario)` from **independent held-out, labeled, representative action outcomes**, not a number spoken by Gemma. Track a conservative **95% upper confidence bound** (`p_bad_upper`) for unsafe decisions in relevant cohorts. Rare cohorts are automatically unknown and reviewed. False negatives on dispute signals and false positives on a charge match require separate evaluation.

For a demonstration with **44 synthetic labeled tickets**, it is not credible to assert `<=3%` unsafe-refund error. Even **hypothetically zero errors in all 44 statistically independent relevant tests** would give a one-sided 95% exact upper bound of approximately **6.6%** (`1 - 0.05^(1/44)`). With zero failures, at least **99 relevant independent cases** would be needed for this bound to fall below 3% under a stationary binomial assumption. Current 44 cases span many scenarios, so actual counts for a refund action are much smaller. Thus: **unknown / not established; manual approval for monetary actions** until measured.

### Measurement 2: monetary action amount

`refund_amount` is **not itself the loss caused by an incorrect decision**. A legally owed refund is a legitimate business cost. Keep separately:
- actual `cash_refunded` and `discount_concession` (booked costs),
- `amount_at_risk` (max money moved by a planned action),
- estimated **incremental loss if decision is wrong** (e.g. unwarranted refund or duplicated refund),
- future revenue and consent/compliance risk from wrong cancellation or retention. `cancel = $0 risk` is unsafe: wrongful cancellation may have economic, user and legal consequences despite no immediate disbursement.

### Measurement 3: expected incremental wrong-decision loss

`E_wrong = p_bad_upper * L_wrong_if_error` is a conservative **screening model**, not a true measured business ROI. In this formula `L_wrong_if_error` is the incremental downside of a **wrong** action. It does not include the correct refund cost; those direct costs must be shown separately. For multi-outcome scenarios use `sum(P(outcome_i) * incremental_loss_i)` and include reversibility, fees, regulatory harm and exposure not easily captured in dollars.

**Hypothetical arithmetic:** if `p_bad_upper=3%`, `L_wrong=$50`, then `E_wrong=$1.50`. If `p_bad_upper=15%`, `L_wrong=$200`, then `E_wrong=$30`. A merchant limit of **$8** would reject the latter; **$8 is NOT a universal Visa/Stripe fee**. If the $200 action can only tolerate `$8` expected wrong loss, `p_bad_upper` must be **at most 4%**, *and all hard gates and the absolute transaction limit still apply*.

![Modeled expected incremental loss under different wrong-decision costs; illustrative hypotheses, not observed performance](defuse_risk_scenarios.png)

### Proposed routing (H thresholds, editable per merchant)

| Scenario | Action | Route |
|---|---|---|
| Clear FAQ / billing explanation from verified facts | Read-only response | **AUTO** if enough evidence; otherwise ask or review |
| Clear and authenticated request to stop a **specific** authorized web renewal | Cancel auto-renew and confirm provider response | **AUTO** if provider supports it; do not obstruct with retention |
| Small refund with **known** calibrated `p_bad_upper <= 3%`, amount <= merchant max, expected wrong loss <= merchant max, every hard gate passed | Issue refund | **AUTO**, only in calibrated production; mock-only in hackathon |
| Any otherwise valid refund where error bound is unknown/above auto limit or refund exceeds auto amount | Build evidence packet | **REVIEW**; trained employee approves exact action |
| Multiple possible payer identities, unsupported provider, dispute open, failed authentication, inconsistent ledger, security allegation | Investigate safely, ask limited clarifying questions | **INVESTIGATE**; no money mutation |
| Failure of policy service/audit/database, no explicit user consent, invalid model output | No financial action | **FAIL CLOSED**; alert/review |

Avoid the seemingly convenient rule `p_err <= 15% -> AI may act`: **15% can be extremely high when money moves**. A wider band may be useful for *draft assistance*, never for bypassing hard policy. A second model can re-extract intent and evidence on ambiguity but may not override provider authorization, missing identity, merchant rules or review requirements.

## 8. Why retention is optional, not a cancellation barrier

The business may prefer lifetime value preservation, but the agent's first responsibility is **to resolve the user's actual authorized request correctly**. Decision sequence:

1. Is there an explicit user request to **cancel renewal**? If yes, authenticate and process the eligible cancellation immediately or provide the correct supported route. Do not ask 'why?' as a mandatory step.
2. Is there a refund demand or potentially unauthorized charge? Independently investigate eligibility and transaction facts. A discount **cannot replace** an owed refund.
3. Is the user simply exploring alternatives due to **price** or **lack of usage**? If allowed, use the observed reason and merchant policy to offer one clear, reversible, opt-in concession; disclose discounted and future full prices.
4. If an offer is rejected, stop. If accepted, execute it safely and log customer consent. A card fingerprint is not a license to combine customer identities or deny rights.
5. Measure incremental saves against a genuine holdout/randomized control group and **net contribution after discounts**, not only raw 'offer accepted'.

California requires simple online cancellation for covered contracts under its automatic-renewal regime [S14]. The EU's online withdrawal function required from 19 June 2026 applies within the statutory **withdrawal window** to covered distance contracts, and is **not** identical to cancelling a recurring renewal at any later date [S32]. The 2024 FTC broad 'click-to-cancel' amendment was vacated in July 2025, but broader US consumer law remains [S15]. Exact legal applicability requires counsel; a merchant's `refund <=14 days AND <=2 sessions` rule is **H commercial policy**, not a universal interpretation of EU withdrawal law.

## 9. Technical and security validation: payments cannot be run by model text

**Architectural principle:** Gemma classifies ambiguous natural language and drafts responses; deterministic domain code owns authorization, action eligibility, parameters and side effects. Gemma 4 31B is an officially listed supported model (`gemma-4-31b-it`) for the Gemini API [S33]. A model being supported does **not** establish that Defuse's live integration or latency works; as-of current project status the live inference has **not been executed**.

- **Stripe**: refunds and cancel-at-period-end API operations are distinct; idempotency and fresh state required. Dispute fees can apply despite a refund when a dispute was already opened [S12, S34].
- **Apple App Store**: server API allows checking subscription status, transactions and refund history; it does not give a generic direct issuer-refund action for every customer request [S27].
- **Google Play**: official Developer APIs permit authorized developer cancellation and full or proportional refund/revoke operations; store purchases are **not all** instruction-only [S28].
- **Security**: OWASP explicitly advises per-tool authorizations, deny-by-default inputs, review of destructive/financial actions, action-bound approvals, minimal API access and validation of effects [S30]. Regex is defense-in-depth at best, not proof of prompt-injection safety.

## 10. Commercial viability: no invented TAM or ROI

**Current evidence supports:** companies in the named ecosystems have subscription products and some are growing; prices and billing pathways exist; the payments/CX responsibilities exist in hiring descriptions; competitors sell action-capable support systems.

**Current evidence does not support:** paid Defuse customers, net-positive profit at these specific product companies, actual per-company refund ratio, number of billing tickets, monthly recurring revenue, existing automation coverage, purchase commitment or proven reductions in chargebacks. Do not use RevenueCat's tracked $16B or app-industry revenue as a direct addressable market.

**Testable commercial hypothesis (H):** Offer a limited billing-only pilot integrated with a read-only subset of ticket and payment data, and a separately approved sandbox action. Compare **verified safe resolutions and manual minutes saved** versus the same merchant's historical/manual baseline; establish technical access and get a proposed pilot budget/LOI. Do not ask only "Would you pay?"; ask who could approve an actual purchase and under what success conditions.

**Cost model (illustrative, not measured):**

`Gross time value = verified_assisted_cases * minutes_saved_per_case / 60 * loaded_support_hourly_cost`

`Net pilot contribution = gross time value + verified incremental avoided dispute fees - AI/vendor fees - approvals/QA/integration costs - incremental wrong-action losses - incremental concessions`

Neither a refunded principal nor a retention offer should be counted as 'saved revenue'. Dispute avoidance requires comparison/experimental identification (or appropriately controlled longitudinal evidence); a high-risk label is not a chargeback outcome.

**Budget validation questions:** current monthly billing case count; median/90th percentile resolution effort; support staffing and Zendesk/Intercom spend; payment-provider rails; error and dispute costs; owner of automation budget; willingness to share anonymized tickets; needed security controls; revenue at risk for delayed cases; likelihood of signing a pilot agreement.

## 11. What to build now, what to defer

**Hackathon / release 1:**
1. Preserve the current Java 17/Spring Boot 3 + React pipeline, Gemma classifier interface, YAML policy, 128 offline tests, 44-ticket eval, 24 offline red-team samples and existing retention logic. These are **reported by the provided state file**, not independently rerun.
2. Add/update **one** realistic mock customer containing two verified subscriptions on different billing rails; include realistic payment/refund/dispute statuses, timestamps, provenance and policy version.
3. Add explicit `RequestAnalysis` schema: multi-intent, goal, cancellation/refund request, quote evidence, tone and threat signal, missing information, and customer language.
4. Add `AutonomyGate`: **hard facts and policy first**, then financial exposure, and **unknown statistical risk means REVIEW for money actions**. Save route/reason and configured H thresholds; no fake 3% production guarantee.
5. Customer web widget plus admin review card showing exact payment and one-click approved action, with after-state verified and shown in audit.
6. Minimal analytical value: reason counts by rail, number AUTO/REVIEW/INVESTIGATE, blocked money actions and one detailed root-cause panel.
7. Run **actual** Gemma live evaluation when API credentials exist and separate results from mock/replay labels. Test at least the cross-rail case and injection attempted to change amount.

**Stage 2:** channel adapters for email, Zendesk and inbound reviews; persistent DB; hosted PSP integrations with least-privilege OAuth/API keys; out-of-distribution/error calibration and model feedback; webhook reconciliation; observed risk-coverage curves; product-incident detection; retention A/B test; enterprise security, audit retention, deletion and compliance review. No automatic code modification or video-avatar features in the current product.

## 12. Validation interview protocol (not a questionnaire to send blindly)

Interview the **billing queue operator**, **support lead**, and **payment/finance owner** separately at 5-8 candidate companies. Have them walk through recent resolved anonymized incidents, not speculative features.

1. Show the last 5 billing cases requiring cross-system lookup; which fields required an agent to leave the helpdesk?
2. For each case, how did staff verify the exact customer, payment provider, subscription and refunded amount?
3. How often was a cancellation successfully registered but another subscription was left active? What caused it?
4. Which payments lead to complaints after a refund is already pending? Are reference numbers available?
5. Does the company already use Fin/Decagon/Zendesk AI, Churnkey or dispute-alert tools, and which process still falls through?
6. What actions may L1 staff perform; what does L2/finance need to approve, and what are actual cash exposure limits?
7. What is the measured difference between ordinary cancellation, refund, voluntary churn and bank fraud/dispute signal?
8. Which support requests are expensive enough to justify an integration rather than a helpdesk macro?
9. What first-party sources (web purchase, app store, entitlements, trial/usage, anti-fraud) can an external processor legally access?
10. What security/data-processing review, measurable pilot success and actual budget are required to buy within a quarter?

**Purchase proof hierarchy:** signed paid pilot > approved budget/LOI > access to real anonymized incident data and named buyer > verbal interest. Current status: **no evidence of paid demand**.

## 13. Validation blockers and explicit UNVERIFIED list

- Individual candidate companies' audited profit/net income and margin (including Leaply, Dressly, PawChamp, RiseGuide, Liven).
- Live personalized US checkout amounts for Leaply; its official policy intentionally delegates price to checkout.
- Each company's merchant-of-record, card processor, App Store/web conversion mix, subscription database, support tool coverage, refund ratio and chargeback ratio.
- Percentage of subscriptions that are duplicates or belong to the same user across rails.
- Willingness to buy Defuse, price to be charged, net financial value and incremental retention improvement.
- Production reliability, latency, per-inference cost and calibration of Gemma 4 in Defuse; 44 synthetic examples and offline tests are not representative production evidence.
- Current production-ready implementation of OAuth/verified identity across PSPs; the state file reports mock connectors, not a certified payments integration.
- Individual legal eligibility outcomes in the proposed example (requires contract/product/jurisdiction specific evidence).

## 14. Sources and evidence strength

Sources below were checked or triangulated in the current research; URLs point to the original pages. `A` for a company's own pricing/terms establishes *what it publishes*, **not** independently audited performance. `C` self-reported business claims must not be repackaged as industry benchmarks.

- **[S1] (A) SKELAR product portfolio:** https://www.skelar.tech/ — confirms product ecosystem and categories.
- **[S2] (C) Leaply CTO / engineering role, positive unit economics:** https://builtin.com/job/cto-leaply/7031049 and https://jobs.dou.ua/companies/skelar/vacancies/324655/ — employer claim, not net profit.
- **[S3] (C) Leaply Payment & Ops engineer:** https://jobs.dou.ua/companies/skelar/vacancies/373433/ — 200k+ users, 60+ team, single payments provider, planned diversification, employer-supplied.
- **[S4] (A) PawChamp membership and refund policy:** https://getpawchamp.com/membership-policy/ — distinct web, Apple and Google handling; compare also https://getpawchamp.com/terms/ .
- **[S5] (A) Leaply subscription policy:** https://theleaply.com/en/subscription-policy/ — 4/12/24-week example periods and dynamic offers, payments and web cancellation.
- **[S6] (A) PawChamp US App Store:** https://apps.apple.com/us/app/pawchamp-dog-puppy-training/id6736829352 — selected US in-app purchase SKUs.
- **[S7] (A) Dressly US App Store:** https://apps.apple.com/us/app/dressly-ai-outfit-maker/id6738174180 — selected US SKUs.
- **[S8] (C) Dressly founder growth claims:** https://jobs.ashbyhq.com/dressly/88b74b53-70e5-4b65-8b7f-0aa997752c40/ .
- **[S9] (A) RiseGuide US App Store:** https://apps.apple.com/us/app/riseguide-self-improvement/id6737224695 .
- **[S10] (A) Liven's own published US pricing explanation:** https://theliven.com/blog/wellbeing/dopamine-management/how-much-does-liven-cost .
- **[S11] (B) RevenueCat 2026, methodology in report:** https://www.revenuecat.com/state-of-subscription-apps .
- **[S12] (A) Stripe dispute fees, when a refund does not remove a dispute:** https://support.stripe.com/questions/dispute-fees-faq .
- **[S13] (A) Visa VAMP 2025 fact sheet and 2026 thresholds:** https://corporate.visa.com/content/dam/VCOM/corporate/visa-perspectives/security-and-trust/documents/visa-acquirer-monitoring-program-fact-sheet-2025.pdf .
- **[S14] (A) California AB2863 text:** https://legiscan.com/CA/text/AB2863/id/3019477 ; text of BPC 17602 should be checked on official state legislation for each merchant's applicable contract.
- **[S15] (A) FTC 2026 negative-option status / rule vacatur:** https://www.govinfo.gov/content/pkg/FR-2026-03-13/html/2026-04952.htm .
- **[S16] (A) Intercom Fin Tasks/Procedures support refunds:** https://www.intercom.com/help/en/articles/9569407-fin-tasks-explained .
- **[S17] (A) Intercom human-in-loop:** https://www.intercom.com/help/en/articles/14468561-human-in-the-loop-approvals-for-fin-procedures .
- **[S18] (C) Decagon/Substack case study (vendor-reported):** https://decagon.ai/case-studies/substack .
- **[S19] (C) Churnkey offers and vendor claims:** https://docs.churnkey.co/cancel-flows/offers/discounts.md and https://churnkey.co/growth/library/pause-offer-at-cancellation .
- **[S20] (C) BBB Liven complaint and company response (individual case):** https://www.bbb.org/us/nv/las-vegas/profile/self-improvement-coach/liven-1086-90093506/complaints .
- **[S21] (A) PlantIn US App Store:** https://apps.apple.com/us/app/plantin-plant-identifier-care/id1527399597 .
- **[S22] (A) Impulse US App Store:** https://apps.apple.com/us/app/impulse-brain-training/id1451295827 .
- **[S23] (A) Promova US App Store:** https://apps.apple.com/us/app/promova-language-learning-app/id1460782849 .
- **[S24] (B/C) Forbes Oct 2026 reporting on Promova (interview-based statements):** https://forbes.ua/innovations/si-rozvivavsia-svidse-movnii-zastosunok-promova-z-orbiti-genesis-proviv-tri-xvili-skorocen-i-pereisov-pid-upravlinnia-kompaniyi-boosters-shho-stalosia-interviu-19102026-43037 .
- **[S25] (A) Wrong Liven same-name listing (do not use):** https://apps.apple.com/us/app/liven/id6737596528 ; SKELAR Liven has App Store id `6450840109`: https://app.sensortower.com/overview/6450840109 .
- **[S26] (C) Leaply monetization + support and online reputation role:** https://secretremote.com/monetization-analyst-leaply-689bb157-0675-41e2-8cd7-c0bde7e9f0f1 .
- **[S27] (A) Apple App Store Server API:** https://developer.apple.com/documentation/appstoreserverapi .
- **[S28] (A) Google Play cancellation/refund/revoke API:** https://developer.android.com/google/play/billing/manage-purchases .
- **[S29] (A) Stripe subscriptions update/cancel:** https://docs.stripe.com/api/subscriptions/update .
- **[S30] (A) OWASP AI Agent Security Cheat Sheet:** https://cheatsheetseries.owasp.org/cheatsheets/AI_Agent_Security_Cheat_Sheet.html .
- **[S31] (A) Stripe refund lifecycle:** https://support.stripe.com/questions/where-is-my-customers-refund .
- **[S32] (A) EU Directive 2023/2673:** https://eur-lex.europa.eu/eli/dir/2023/2673/oj/eng .
- **[S33] (A) Gemma 4 supported on Gemini API:** https://ai.google.dev/gemma/docs/core/gemma_on_gemini_api .
- **[S34] (A) Stripe idempotency guidelines:** https://docs.stripe.com/api/idempotent_requests .
- **[S35] (A) SKELAR Billing & Payments specialist role:** https://jobs.dou.ua/companies/skelar/vacancies/375385/ .
- **[S36] (A) Zendesk pricing and action-enabled AI agents:** https://www.zendesk.com/pricing/ .

---

**Recommendation for review:** Approve this narrowed ICP and decision pipeline. Keep every financial and legal parameter configurable per merchant. Treat `p_bad` as **unknown** until statistically evaluated; all live refund workflows then require review, while a synthetic sandbox can demonstrate merchant-configured AUTO. The developer handoff file `Defuse_Developer_Spec_v1_2026-10-31.md` translates these choices into contracts, workflow, tests and acceptance criteria.
