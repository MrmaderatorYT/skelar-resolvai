# End-to-end flow — retention

1. Ticket "too expensive, please cancel" → classifier returns `intent=cancel`, `cancel_reason=too_expensive`.
2. `ContextBuilderService` adds the profile: acquisition channel, tenure, paid renewals, offers in the cooldown window (90 days), offers on linked accounts (card fingerprint), offers sent in 30 days, holdout flag.
3. `ReferencePolicyService.retentionOffer()` checks eligibility → `offer_discount` / `offer_pause`, or `cancel` with `retention_not_eligible: <reason>`.
4. Executor records the offer (counts toward the 90-day cooldown); reply makes one offer and says "reply CANCEL to cancel anyway". Status `offer_sent`.
5. Customer accepts → `apply_discount` or `pause`; declines → `cancel` immediately. No second offer.
