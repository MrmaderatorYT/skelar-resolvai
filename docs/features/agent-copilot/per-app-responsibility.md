# Per-app responsibility — agent-copilot

| Part | Responsibility in this feature |
|---|---|
| backend (Spring Boot 3, Java 17+) | ticket detail, reply generation, approve/reject |
| frontend (React) | ticket screen, reply editor, customer panel |
| Claude API | Sonnet 5.5: reply in the customer's language, constrained by the decision |
| scripts | seed data, eval, keyword baseline |
