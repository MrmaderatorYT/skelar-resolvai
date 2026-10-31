# Architecture

```
Frontend (React + Vite + TypeScript + Tailwind + Recharts)
  Queue · TicketView · Dashboard · Eval
        │  /api (Vite proxy → :8000)
Backend (Spring Boot 3, Java 17+)   com.skelar.defuse
  TicketPipelineService.analyze(ticket)
    1. InjectionGuardService       regex, before any LLM
    2. LlmClassifierService        Claude Haiku 5.5 → ClassificationFeatures (structured output)
    3. ContextBuilderService       Customer 360 = read-only "tools" (plan, processor, payments, usage)
    4. ReferencePolicyService      deterministic: actions, mode, amount, reasons (port of reference_policy.py)
    5. ActionExecutorService       MockBillingService, idempotent per ticket:action, payment-state checks, audit
    6. ReplyWriterService          Claude Sonnet 5.5, written AFTER execution from actual results
  InMemoryStore    seeded from resources/data/*.json, POST /api/demo/reset
  Analytics        session KPIs vs synthetic human-only baseline; EvalService (45 labelled tickets)
```

**Rule: the LLM understands and writes; only code moves money.**

Modes: `auto` (Defuse executes) · `suggest` (a requested cancel runs now, money waits for Approve) · `escalate` (no billing writes; read-only evidence pack allowed).

LLM modes (`defuse.llm.mode` / env `DEFUSE_LLM_MODE`): `gold` replays eval labels (no API; tests and offline demo — label it as replay on screen); `live` calls Claude.
