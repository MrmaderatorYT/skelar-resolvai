# Data model — action-engine

Reads: `payments` (status, amount, processor, created_at), `subscriptions` (status, plan, price), `usage` (sessions_since_last_charge, cancel_flow_abandoned).
Writes: `payments.status=refunded` + `refunded_amount`, `subscriptions.status=canceled|paused`, `current_period_end`, `customers.ltv_usd`, `store.executed`, `store.evidence`, audit entries.

Full schemas: `backend/data/*.json`, see repo README.
