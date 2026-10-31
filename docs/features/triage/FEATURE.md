# Triage

Classifies multilingual subscription-support tickets with Gemma and routes deterministic policy decisions. Live semantic quality is UNVERIFIED until an API-key run; offline parsing and guard safety are tested. Provider: gemma; documented default gemma-4-31b-it.

Modes: gold (label replay), live (actual model), replay (recorded live analyses, read-only). All are explicitly identified in API provenance. Key only in backend env. Classifier cannot set refund amounts or execute actions.

Sources and account verification table: integrations.md. Frozen ClassificationFeatures and PolicyDecision records are unchanged.
