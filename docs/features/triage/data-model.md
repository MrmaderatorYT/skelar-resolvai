# Data model — triage

Reads: `tickets_eval.json` (text, channel, language), `customers.contacts_last_30d`.
Writes: `analysis.features` = {intent, true_goal, sarcasm, hidden_aggression, explicit_aggression, dispute_signal, injection, sentiment, language, confidence, evidence[]}, `decision.risk_score`, `decision.risk_band`, `decision.priority`.

Full schemas: `backend/data/*.json`, see repo README.
