# Business context — action-engine

- Refund is cheaper than a chargeback: $49.99 refund vs $49.99 + $15–25 fee + $8 VAMP fee + 1★ review.
- Web purchases: full refund ≤ 14 days and ≤ 2 sessions since the charge; 50% ≤ 30 days; outside the window but high risk → goodwill 50% via a human.
- App Store / Google Play: only Apple/Google can refund → we send exact steps.
- Auto-refund limit $60; refund/chargeback history or LLM confidence < 0.85 → human approval.
- A cancellation the customer asked for moves no money, so it never waits for a human.
- Open dispute → never refund (would pay twice) → evidence pack for representment.
