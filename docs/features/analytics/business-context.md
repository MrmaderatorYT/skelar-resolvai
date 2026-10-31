# Business context — analytics

- Support should tell product what to fix: 'charged after trial' is the top cause → reminder 24h before the trial ends.
- P(chargeback | risk band) comes from the history baseline (synthetic: high ≈ 23.5%, medium ≈ 2.4%).
- A refund does not save the charge itself. We show three numbers separately: gross exposure (charge + est. fees), refund spend, and expected fees avoided = P(dispute | band) × (Stripe $15 dispute fee + scheme fee from the acquirer contract, default 0). Also: discount cost and save rate vs the holdout.
- Baseline data is synthetic — label it as such, never present it as real results.
