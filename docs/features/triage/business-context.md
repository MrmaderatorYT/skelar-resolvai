# Business context — triage

- Billing tickets ('charged after trial', 'can't find cancel') are the #1 source of chargebacks for subscription apps.
- Visa VAMP: the merchant 'Excessive' threshold dropped from 2.2% to 1.5% on 1 Apr 2026 (US/CA/EU/AP); above it, $8 per dispute plus acquirer pressure.
- Classic sentiment reads sarcasm as positive: on our eval the keyword bot labelled 7 of 9 sarcastic tickets as positive.
- Rule: risk = 0.10 + 0.40·dispute + 0.25·sarcasm + 0.20·aggression + 0.15·money intent + 0.10·contacts in 30d (max 3). high ≥ 0.5, medium ≥ 0.3.
- Priority: P1 = high risk, injection, account takeover or open dispute; P2 = medium or account access; P3 = has an action; P4 = questions.
