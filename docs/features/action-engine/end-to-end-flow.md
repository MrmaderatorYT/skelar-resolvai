# End-to-end flow — action-engine

1. `build_context(customer_id)` reads plan, platform, last payment, sessions, refund/chargeback history, duplicate charges.
2. `decide(features, ctx, confidence)` returns actions, mode, refund amount, reasons.
3. `ActionExecutorService.actionsToRunNow()`: auto → all; suggest → cancel + safe actions; escalate → safe actions only.
4. `ActionExecutorService.execute()` calls `service/executor/MockBillingService.java`; key `ticket_id:action` makes it idempotent.
5. Every execution or failure is written to the audit log.
6. Agent clicks Approve → remaining actions run (`pipeline.approve`).

Approval must include the `analysis_id` returned by the reviewed analysis. The server rejects an old analysis ID or changed billing context; reanalyze and review again.
