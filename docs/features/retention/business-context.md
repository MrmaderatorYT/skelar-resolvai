# Business context — retention

- Answers the buyer's objection "why pay for something that cancels faster?": Defuse also keeps customers for whom an offer fits.
- Vendor data (C): a good cancel flow saves 10–34% of cancel attempts; Churnkey starts at 30% for 3 months and reports 54% of paused subscribers resume vs 22% retained by discounts. Every value: `docs/evidence/RULES.md`.
- Law: California AB 2863 (from 1 July 2025) allows a save offer if the business also tells the customer they can still cancel; if they persist, cancel promptly. Our rule: one offer per ticket, "CANCEL" always works.
- Eligibility (all must hold): cancel request with an offerable reason; no dispute threat or aggression; no repeat contact in 30 days; tenure ≥ 2 months and ≥ 1 paid renewal; no offer in the last 90 days (Churnkey cooldown); no offer to another account with the same card fingerprint; no refund/chargeback history; monthly offer budget not used up; not in the 10% holdout.
- The LLM only names the cancel reason; offer type, depth and eligibility are code.
