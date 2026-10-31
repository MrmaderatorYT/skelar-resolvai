# Business context — action-engine

- A refund is a cost, not a saving. Resolving before the bank avoids the dispute fee (Stripe US $15, +$15 if contested and lost), the hit to the VAMP ratio and a 1★ review. See `docs/evidence/RULES.md`.
- Web purchases: full refund ≤ 14 days and ≤ 2 sessions since the charge (EU withdrawal period); pro-rata unused days ≤ 30 days (CRD art. 14(3), CJEU C-641/19); outside the window but high risk → pro-rata goodwill via a human.
- App Store / Google Play: only Apple/Google can refund → we send exact steps.
- Auto-refund limit $60; refund/chargeback history or LLM confidence < 0.85 → human approval.
- A cancellation the customer asked for moves no money, so it never waits for a human.
- Open dispute → never refund (would pay twice) → evidence pack for representment.
