# Triage — intent, hidden emotion, dispute risk, priority

Reads every incoming ticket, finds the customer's real goal, detects sarcasm, hidden and explicit aggression, dispute signals and prompt injection, scores chargeback risk and sets priority P1–P4.

**Who uses it:** support agents, support lead, product team.

**Main metric:** Sarcasm recall ≥ 80%, intent accuracy ≥ 85%, 0 missed injections (service/analytics/EvalService.java (POST /api/analytics/eval/run, ./gradlew liveEval)).

**Status:** MVP (hackathon, 31.10.2026).
