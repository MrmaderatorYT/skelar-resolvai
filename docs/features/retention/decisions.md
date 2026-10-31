# Decisions — retention

- **One offer per ticket, CANCEL always works** — AB 2863 "one save" and customer trust.
- **Eligibility in code, not in the LLM** — the model only names the reason; it cannot hand out discounts.
- **Holdout 10%** (customer id % 10 == 3) — without it we cannot tell saved customers from ones who would have stayed anyway.
- **No offers under dispute threat or aggression** — those go to the refund/cancel path, no bargaining.
- **Card-fingerprint clusters** — a second account on the same card does not get a fresh discount.
- **Offer is counted when sent, not when accepted** — declining does not reset the cooldown.
