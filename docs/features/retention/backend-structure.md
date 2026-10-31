# Backend structure — retention

- `model/domain/Customer.java` — `acquisitionChannel`, `funnel`, `quizGoal`, `cardFingerprint`, `offers12m`, `lastOfferAt`
- `model/dto/CustomerContext.java` — `tenureMonths`, `paidRenewals`, `offers12m`, `identityOffers12m`, `retentionOffers30d`, `inHoldout`
- `service/policy/ReferencePolicyService.java` — `RETENTION_OFFERS`, limits, `retentionOffer()`
- `service/executor/MockBillingService.java` — `offer_discount`, `offer_pause`, `apply_discount`, `pause`
- `service/TicketPipelineService.java` — status `offer_sent`, `acceptOffer()`, `declineOffer()`
- `service/analytics/AnalyticsService.java` — `retention` block in the summary
