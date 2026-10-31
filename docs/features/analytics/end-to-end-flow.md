# End-to-end flow — analytics

1. Every analysis stores money, decision and features.
2. `GET /api/analytics/summary` aggregates the session and the baseline.
3. `GET /api/analytics/root-causes` groups the history by root cause with chargeback rate and a fix.
4. `EvalService.runLive()` (`POST /api/analytics/eval/run` or `./gradlew liveEval`) runs the live classifier on the 38 labelled tickets → `eval_report.json`; the keyword-bot baseline comes from `defuse-data` → Eval page.
