# Business context — analytics

- Support should tell product what to fix: 'charged after trial' is the top cause → reminder 24h before the trial ends.
- P(chargeback | risk band) comes from the history baseline (synthetic: high ≈ 23.5%, medium ≈ 2.4%).
- A refund does not save the charge itself. We show three numbers separately: gross exposure (charge + est. fees), refund spend, and expected fees avoided = P(dispute | band) × ($20 chargeback fee + $8 VAMP fee, est.).
- Baseline data is synthetic — label it as such, never present it as real results.
