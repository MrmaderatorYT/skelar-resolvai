# Decisions — agent-copilot

- **Reply is generated after the decision** and receives it as input, so the text cannot promise more than the policy allowed.
- **Gold labels never leave the backend** (`Ticket.gold` is WRITE_ONLY in Jackson).
- **Evidence highlighting builds React nodes**, never HTML from ticket text (XSS).
