# Backend structure — agent-copilot

- `service/llm/ReplyWriterService.java` — `write()` (Sonnet, after execution, constrained by the decision), `template()`
- `service/llm/Prompts.java` — `REPLY_SYSTEM`
- `controller/TicketController.java + ActionController.java` — ticket detail, create, approve, reject
