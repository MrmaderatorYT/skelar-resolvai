# Responsibilities

Core policy owns permission and money amounts. Executor owns dispatch, capability checks, existing idempotency, audit and failure escalation. Billing providers own mock ledger writes. Sources expose read-only snapshots; they do not submit replies or mutations. Spring owns bean lifecycle and startup failure. Deployment owns env secrets. UI does not select plugins or edit money policy.
