# Backend structure — analytics

- `service/analytics/MoneyService.java` — `pDisputeByBand()`, `atRisk()`
- `service/analytics/AnalyticsService.java` — `summary()`, `rootCauses()`, `FIXES`
- `controller/AnalyticsController.java`
- `service/analytics/EvalService.java (POST /api/analytics/eval/run, ./gradlew liveEval)`, `defuse-data/evaluate.py`, `defuse-data/baseline_keywords.py`
