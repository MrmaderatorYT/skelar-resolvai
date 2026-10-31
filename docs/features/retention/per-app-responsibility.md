# Per-app responsibility — retention

| Part | Responsibility |
|---|---|
| backend (Spring Boot) | profile in Customer 360, eligibility, offer record, accept/decline, metrics |
| frontend (React) | offer card with Customer accepts / declines, profile rows, retention KPIs |
| Claude API | classifier names `cancel_reason`; reply makes exactly one offer and states the customer can still cancel |
| defuse-data | reference eligibility (`retention_offer`) and the 6 labelled retention tickets |
