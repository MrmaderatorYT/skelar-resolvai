# Data model — retention

Customer: `acquisition_channel`, `funnel`, `quiz_goal`, `card_fingerprint`, `offers_12m`, `last_offer_at`.
Subscription: `discount_percent`, `discount_until`, `pause_until`.
Features: `cancel_reason` ∈ too_expensive | no_time | not_using | cant_find_cancel | technical | other | none.
Context: `tenure_months`, `paid_renewals`, `offers_12m`, `identity_offers_12m`, `retention_offers_30d`, `in_holdout`.

Schema mirrors real billing/attribution data (and the open KKBox churn dataset: registration channel, transactions, cancels); values are synthetic.
