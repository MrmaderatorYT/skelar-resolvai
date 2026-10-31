# Per-app responsibility — action-engine

| Part | Responsibility in this feature |
|---|---|
| backend (Spring Boot 3, Java 17+) | policy, executor, mock billing, audit |
| frontend (React) | decision card with Approve / Escalate |
| Claude API | none — the model never decides money |
| scripts | seed data, eval, keyword baseline |
