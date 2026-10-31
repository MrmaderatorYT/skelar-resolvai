# RULES — every policy number and where it comes from

Strength: **A** law, card-network rules, official company policy/docs · **B** large report with method, or official numbers quoted by a reputable secondary source · **C** vendor data without published method · **H** our heuristic, no external source — measured by the eval set and the retention holdout.
Values live in `backend/src/main/resources/defuse-policy.yml`; the Python reference (`defuse-data/reference_policy.py`) holds the same numbers and a test keeps them equal.

| Key | Value | Type · strength | Source | Why this value |
|---|---|---|---|---|
| `refund.full-max-days` | 14 | business rule · H (legal context A) | Context: EU CRD 2011/83/EU art. 9 gives a 14-day withdrawal period for distance contracts, with exceptions for digital content/services and consent rules that depend on the product and national law | Our demo rule mirrors the 14-day window; it is **not** a claim that CRD obliges a full refund in every case. Real decisions per jurisdiction/product → configurable, ambiguous cases → human |
| `refund.full-max-sessions` | 2 | heuristic · H | — (CRD art. 14(3) is the reason usage matters) | "Barely used" proxy: opening the app once or twice is not consuming the service |
| `refund.partial` | pro-rata unused days | business rule · H (legal context A) | Context: CRD art. 14(3) and CJEU C-641/19 use time-proportional amounts when a consumer withdraws after service started | We borrow the proportional logic as a fair, explainable default; outside withdrawal it is a company choice, not a legal duty |
| `refund.partial-max-days` | 30 | business rule · H (benchmark A) | Calm refunds web purchases within 30 days (official policy; full refund, not partial); Liven advertises a 30-day guarantee (C) | Same window length as comparable wellness apps; the pro-rata amount is our choice |
| `refund.goodwill` | pro-rata unused, human approves | heuristic · H | Dispute economics below | Outside the window but likely to dispute: a person decides, amount follows the same pro-rata logic |
| `refund.auto-limit-usd` | 50 | heuristic · H | — | Spec E4 illustrative cap: covers monthly and quarterly plans ($49.99 fits, $50.01 does not); annual plans ($89.99) always get a human |
| refund/chargeback history → human | on | heuristic · H | Stripe: same-card detection exists to stop repeat abuse (A, see below) | Serial refunders are the abuse case; a person looks before money moves again |
| store purchases | Apple: instructions; Google Play: refund possible via developer, demo routes to instructions/human | official policy · A | Apple: customers request refunds via reportaproblem.apple.com; Google Play: developers can refund orders via Play Console / API | Demo store provider declares no REFUND capability; a production Google Play provider can declare it (capability-based plugins) |
| `retention.discount-percent` | 30 | vendor docs · C | Churnkey: "30% off for 3 months" is the common start; 30% is "the minimum starting point for meaningful acceptance rates" | Below ~30% acceptance is low; deeper trains customers to cancel for deals |
| `retention.discount-months` | 3 | vendor docs · C | Churnkey (same page) | Time to see value while capping revenue impact |
| `retention.pause-months` | 2 | vendor data · C | Churnkey pause study: use 2–3 lengths tuned to the usage cycle | Shortest of the recommended lengths |
| reason → offer | too_expensive → discount; no_time, not_using → pause | vendor data · C | Churnkey: 54% of paused subscribers resume vs 22% retained by save discounts (method not published) | Pause works better, so it is the default when price is not the stated reason |
| cancel first, one optional win-back | on | law · A | California AB 2863 (from 1.07.2025): save offer allowed if the customer is told they can still cancel; cancel promptly if they persist | The renewal is canceled at once; the offer is one optional restore-or-pause, never a replacement for the cancel |
| `retention.cooldown-days` | 90 | vendor docs · C | Churnkey recommends a 3-month Discount Cooldown Period "to prevent repeated discount use" | Industry default for stopping repeat discounts |
| same card on several accounts | counts as one customer | official docs · A | Stripe: card fingerprint lets you "detect someone trying to sign up multiple times with the same card (for example, to get the same discount)" | Stops "new email → new discount" |
| `retention.min-tenure-months` / `min-paid-renewals` | 2 / 1 | heuristic · H | RevenueCat SOSA 2026: only ~5% of annual-plan cancellers ever come back (B, via SaaStr summary) — saving established customers is worth more | Offers go to customers who already paid at least once |
| `retention.max-offers-per-30d` | 50 | heuristic · H | — | Budget cap so a bug or a viral complaint cannot hand out unlimited discounts |
| `retention.holdout-bucket` | 10% (id % 10 == 3) | heuristic · H | Standard experiment design; vendors report save rates without holdouts, so we add one | Without a holdout we cannot tell saved customers from ones who would have stayed |
| no offers under dispute threat / aggression | on | heuristic · H + law A | AB 2863: cancel promptly when the customer insists | Bargaining with someone threatening a chargeback raises dispute risk |
| `money.dispute-fee-usd` | 15 | pricing · B | Stripe US dispute fee $15 (+$15 counter fee if contested and lost, since 17.06.2025), via Chargeflow/Redo summaries of Stripe pricing | Real cost of each chargeback on top of the charge |
| `money.scheme-fee-per-dispute-usd` | 0 (configurable) | contract · — | Visa's public VAMP fact sheet does not publish a universal per-dispute fee | Set it from your acquirer contract; we do not invent one |
| `money.vamp-threshold` | US/CA/EU/AP 1.5%, CEMEA 2.2% | card-network rules · A | Visa VAMP fact sheet: 2.2% → 1.5% from 1.04.2026 for AP/Canada/EU/US; CEMEA stays 2.2% (count ≥ 1,500 / ≥ 150) | Ukraine is CEMEA; US/EU merchants face 1.5% |
| Mastercard ECP | 1.5% and ≥ 100 chargebacks / month | card-network rules · B | Vendor summaries of Mastercard ECP | Second network with the same ratio |
| `risk.weights` | dispute .40, sarcasm .25, aggression .20, money .15, per contact .10 | heuristic · H | Definitions: `LABELING.md` (Riloff 2013, escalation research) | Ordering follows cost: a dispute signal is the strongest predictor of a chargeback. Measured on the 44-ticket eval; recalibrate on real data |
| `gate.priors` | per intent, e.g. refund .02, account_access .10, unclear .50 | heuristic · H | — | p_err is the hand-set prior only; the model's self-score is never turned into an error rate (spec E1) |
| `gate.min-auto-confidence` | 0.97 | heuristic · H | — | Abstention threshold on the model's self-score, not a probability: below it at most ASSIST |
| `gate.min-assist-confidence` | 0.85 | heuristic · H | — | Below it HUMAN |
| `gate.risk-status` | UNVALIDATED | evidence status | — | No externally measured error bound yet: `p_bad_upper_95` is null and `n_relevant` is 0 in every decision (spec E1) |
| `gate.auto-refund-enabled` | true | business switch | — | false sends every refund to a human |
| `gate.sandbox` | true | environment | — | Mock billing only. With any real provider set it to false: UNVALIDATED refunds then go to REVIEW (`risk_not_calibrated`) (spec E4) |
| `refund.min-auto-confidence` | 0.85 | heuristic · H | — | Policy floor on every money path: below it an auto decision becomes suggest (`low_confidence`); the autonomy gate has its own, stricter 0.97 abstention (`gate.min-auto-confidence`) |

## What changed versus the first version
- **Partial refund: flat 50% → pro-rata unused days** (CRD 14(3), C-641/19). T04: $14.99 → $9.99 (10 of 30 days unused); T10 goodwill: $24.99 → $28.01 (51 of 91 days).
- **Discount: 25% × 2 months → 30% × 3 months** (Churnkey).
- **Offer cooldown: 365 → 90 days** (Churnkey). T40's previous offer moved to 60 days ago so the "discount seeker" case still shows.
- **Fees: $20 + $8 "VAMP" → $15 Stripe dispute fee + configurable scheme fee (default 0).** The $8 was not in Visa's public documents.

## Sources
- Directive 2011/83/EU, art. 9 and 14 — https://www.legislation.gov.uk/eudr/2011/83/article/14
- CJEU C-641/19 PE Digital — case notes: https://www.lewissilkin.com/insights/2020/10/16/how-to-end-a-bad-dateing-site-membership-102gib6 , https://www.lto.de/recht/nachrichten/n/eugh-rs-c64119-widerruf-verbraucher-parship-verbraucherschutzrichtlinie
- Calm refund policy — https://support.calm.com/hc/en-us/articles/360008240974
- Liven 30-day money-back (secondary) — https://legalclarity.org/reya-digital-ou-charge-how-to-cancel-and-get-a-refund/
- Churnkey discounts — https://docs.churnkey.co/cancel-flows/offers/discounts.md
- Churnkey pause vs discount — https://churnkey.co/growth/library/pause-offer-at-cancellation
- California AB 2863 — https://www.venable.com/insights/publications/2024/09/california-amends-autorenewal-law-with-stricter , https://dwt.com/insights/2024/10/ab-2863-updates-california-automatic-renewal-law
- Stripe duplicate cards — https://support.stripe.com/questions/how-can-i-detect-duplicate-cards-or-bank-accounts
- Stripe dispute fees — https://redo.com/resources/articles/chargebacks/stripe-chargeback-fees , https://www.chargeflow.io/blog/stripe-dispute-fees
- Visa VAMP fact sheet — https://corporate.visa.com/content/dam/VCOM/corporate/visa-perspectives/security-and-trust/documents/visa-acquirer-monitoring-program-fact-sheet-2025.pdf
- Mastercard ECP — https://redo.com/resources/articles/chargebacks/mastercard-excessive-chargeback-program
- RevenueCat SOSA 2026 — https://www.revenuecat.com/state-of-subscription-apps , summary https://saastr.com/the-top-10-learnings-from-revenuecats-state-of-subscription-apps-how-115000-mobile-apps-deliver-16b-in-revenue-whats-working-whats-quietly-killing-growth

## Autonomy gate

The initial per-intent error rates and AUTO/ASSIST thresholds in `defuse-policy.yml` are **heuristic priors (H)**, not calibrated production error rates. `p_err = max(prior, 1 − confidence)` and `expected loss = p_err × impact`; the stricter of policy and gate applies. Measure model errors on held-out labelled real-world tickets before treating the priors as empirical. The Python reference currently mirrors defaults and does not load YAML.
