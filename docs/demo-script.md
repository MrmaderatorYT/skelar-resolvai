# Demo script (2:30–3:00)

Start: `./gradlew bootRun` (backend/), `npm run dev` (frontend/), Queue page, click **Reset demo**.

1. **0:00** Queue: 38 open tickets in 5 languages. "This is a support agent's morning."
2. **0:15** **Analyze all** → re-sorted by priority, risk badges and signal chips appear.
3. **0:35** **T01** — sarcasm ("Truly a masterclass in customer care 👏"): literal tone positive, Defuse flags sarcasm, real goal, high risk, abandoned cancel flow → AUTO cancel + $49.99 refund + reply. Shows chargeback exposure and expected fees avoided (a refund is a cost, not a saving).
4. **1:05** **T05** — polite hidden threat about the bank → high risk → auto.
5. **1:20** **T12** — $89.99 > $60 limit → cancel done, refund waits → **Approve** → audit log.
6. **1:40** **T30** — injection hidden in an HTML comment → escalated, zero actions.
7. **1:55** **T37** — dispute already open → no refund (would pay twice), evidence pack built.
7b. **2:05** **T39** — "too expensive, cancel": eligible → one offer (25% × 2 months) + "reply CANCEL anytime" → **Customer accepts** → discount applied. Then **T40** (discount seeker, offer 150 days ago) and **T41** (second account, same card) → honest cancel, offer withheld.
8. **2:10** **New ticket** live in Ukrainian: «Дякую, дуже чесно списати 29.99 після скасування 🙂» (live mode).
9. **2:25** **Dashboard**: auto-resolved %, refund spend vs expected fees avoided, top root cause (32% charged after trial) → product fix.
10. **2:45** **Eval**: keyword bot read 7/9 sarcastic tickets as positive and made 2 unsafe refunds → Defuse: 0.
