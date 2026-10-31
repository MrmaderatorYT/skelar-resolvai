# Decisions

Use Spring beans instead of PF4J/ServiceLoader: MVP plugins ship with one application and need neither runtime jar loading nor an additional lifecycle. Unknown type and duplicate keys fail startup. Customer/engagement/ticket source interfaces are read-only; the design draft's TicketSource.reply mutation is deliberately excluded to preserve the brief's read-only requirement. Guardrails are enforced again at executor/provider boundaries. No policy logic or frozen DTO fields change. Postgres stretch was not started; the core C1/C2/C3 deliverables take precedence.
