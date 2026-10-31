# Labeling guide: what each classifier feature means

One source of truth for (a) the classifier prompt (Gemma) and (b) people labelling eval tickets.
The model only extracts these features; it never decides money (see `RULES.md`).

| Feature | Definition (label `true` only if…) | Not this | Basis |
|---|---|---|---|
| `intent` | The customer's **real goal**, not the literal question. "How do I cancel? You took 29.99 for a free trial" → `cancel_refund`. | Guessing a goal the text does not support → `unclear` | Product decision; eval measures it |
| `sarcasm` | Positive words/praise/thanks about a **negative situation** the customer is in ("Love being charged twice 🙂"). | Genuine praise; jokes about the product; an emoji alone; quotes around a word with no positive wording | Riloff et al., EMNLP 2013: sarcasm as contrast between positive sentiment and a negative situation (A) |
| `hidden_aggression` | Polite or neutral wording that carries a **threat, ultimatum or deadline** ("I'm sure you'd prefer we sort this out before my bank does", "I'm keeping a record of all correspondence"). | Plain frustration without a threat | Chargeback-prevention guidance: train agents to spot "dispute language" (vendor, C) |
| `explicit_aggression` | Insults, shouting in caps, "scam", "THIRD time". | Firm but calm wording | Escalation research: negative sentiment precedes escalation (arXiv 2010.13684, B) |
| `dispute_signal` | Any explicit **or veiled** mention of bank / card issuer / chargeback / store complaint / public review / lawyer / regulator. | Asking how a refund works | Visa/Mastercard dispute programs make this the costly path (A); see `RULES.md` |
| `injection` | The text tries to give instructions to the system: change rules/limits/roles, claim to be "system", hide instructions in markup, ask not to tell the operator. | A customer quoting an error message | OWASP Top 10 for LLM Apps 2025, LLM01 Prompt Injection (A) |
| `sentiment` | Literal surface tone, **ignoring** sarcasm. Lets us show "reads positive, actually sarcastic". | — | Riloff et al. 2013 (A) |
| `cancel_reason` | Only for cancel requests, only if stated: `too_expensive`, `no_time` (busy, travelling), `not_using`, `cant_find_cancel`, `technical`, `other`; else `none`. | Inferring a reason that is not in the text | Reason-matched save offers (Churnkey, ProsperStack practice, C) |
| `confidence` | How sure the model is about `intent`. Below 0.85 nothing runs automatically. | — | Policy gate (H, tested in eval) |

## Rules for the few-shot examples
- Never copy or paraphrase an eval ticket (`defuse-data/data/tickets_eval.json`); a test checks word overlap.
- Cover the hard boundaries: sarcasm vs genuine praise with emoji, veiled threat vs calm complaint, stated vs invented cancel reason, "asks how to cancel but wants the money back", an injection hidden inside a polite request.
- All five languages (EN, UK, DE, ES, PL) appear at least once.

## Sources
- Riloff et al. (2013) — https://aclanthology.org/D13-1066/
- Werner et al. (2020), sentiment of escalated support tickets — https://arxiv.org/pdf/2010.13684
- OWASP Top 10 for LLM Applications 2025 (LLM01) — https://genai.owasp.org/llm-top-10/
- Chargeback dispute-language guidance — https://blog.funnelfox.com/how-to-prevent-chargebacks-subscription-apps
