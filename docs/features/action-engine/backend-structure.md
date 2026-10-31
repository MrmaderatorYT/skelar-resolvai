# Backend structure — action-engine

- `service/policy/ReferencePolicyService.java` — constants, `decide()`; `ContextBuilderService.build()`
- `service/executor/ActionExecutorService.java` — `actionsToRunNow()`, `execute()`
- `service/executor/MockBillingService.java` — one function per action, `BillingException`, `MONEY_ACTIONS`, `SAFE_ACTIONS`
- `service/audit/AuditService.java` — append-only log (in memory)
- `service/TicketPipelineService.java` — `analyze()`, `approve()`, `reject()`
