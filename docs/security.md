# Security

1. The LLM has no write tools. Only `ActionExecutorService.execute()` changes billing, and only after `decide()`.
2. Ticket text is passed as data inside `<ticket>` tags, never into the system prompt.
3. Prompt injection: regex guard (before the LLM) OR the model's `injection` flag → `escalate`, zero actions.
4. Money gate on every refund path (incl. duplicates): auto-refund ≤ $60; refund/chargeback history → human; only `succeeded` card payments; never a future-dated payment; never more than the payment; never on a disputed payment; store purchases → instructions only. Routing by billing processor, not device.
5. Idempotency: `ticket_id:action` — re-analysis or double Approve never refunds twice.
6. Audit log for every analysis, execution, failure, approval. The reply is written after execution; a failed action escalates instead of claiming success.
7. LLM output is a typed record; unknown intent or non-finite / out-of-range confidence → escalate. API error or refusal → confidence 0 → human.
8. Gold labels never leave the backend. Ticket text rendered as React nodes, never `innerHTML`.
9. API key only in the backend environment (`ANTHROPIC_API_KEY`), never in the frontend or repo. CORS: localhost only. Ticket length ≤ 5000 chars.
10. Demo scope: no identity/authorization layer — synthetic data on localhost only. Before real data: server-side identity, ownership and agent rights, payment-level locks and provider idempotency keys.
