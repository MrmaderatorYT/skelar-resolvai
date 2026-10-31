# Data layer: provider-shaped tables

Defuse runs on a SQL database whose tables follow the published API objects of the systems a subscription business usually connects to. The data is synthetic (`data/*.json`). The tables approximate the public schemas; they are not live integrations.

- Default: H2 in memory, `MODE=PostgreSQL`, rebuilt from the seed on every start.
- `postgres` profile: `DEFUSE_DB_URL`, `DEFUSE_DB_USER` and `DEFUSE_DB_PASSWORD` come only from the environment. Startup seeds an empty database and never erases a non-empty one.
- `demo-console` profile: enables the H2 console for local development. It has no login.

## Tables and their sources

| Table | Source object (public docs) |
|---|---|
| `stripe_customers` | Stripe Customer: https://docs.stripe.com/api/customers/object |
| `stripe_subscriptions` | Stripe Subscription: https://docs.stripe.com/api/subscriptions/object |
| `stripe_charges` | Stripe Charge: https://docs.stripe.com/api/charges/object |
| `stripe_refunds` | Stripe Refund: https://docs.stripe.com/api/refunds/object |
| `stripe_disputes` | Stripe Dispute: https://docs.stripe.com/api/disputes/object |
| `appstore_transactions` | App Store JWSTransactionDecodedPayload: https://developer.apple.com/documentation/appstoreservernotifications/jwstransactiondecodedpayload |
| `appstore_notifications` | App Store Server Notifications V2: https://developer.apple.com/documentation/appstoreservernotifications/responsebodyv2decodedpayload |
| `play_subscriptions` | Google Play purchases.subscriptionsv2: https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2 |
| `rc_subscribers`, `rc_entitlements` | RevenueCat subscribers and entitlements: https://www.revenuecat.com/docs/api-v1 |
| `zendesk_tickets` | Zendesk Ticket: https://developer.zendesk.com/api-reference/ticketing/tickets/tickets/ |
| `appsflyer_installs` | AppsFlyer raw install data: https://support.appsflyer.com/hc/en-us/articles/208387843 |
| `app_users` | Internal identity mapping, owned by the application |
| `app_subscriptions`, `app_payments`, `app_history`, `app_usage` | Demo projections that keep the original JSON, so no domain field is dropped |

Field names follow the published objects. Where the seed has no value for a provider field, the column stays NULL. Two known simplifications: `stripe_subscriptions.current_period_start` is NULL, and `billing_interval` stores the plan name, not a Stripe interval.

## Seed mapping

- `card` payments and subscriptions go to `stripe_*`.
- `app_store` goes to `appstore_*` and `rc_*`.
- `google_play` goes to `play_*` and `rc_*`.
- `tickets_eval` goes to `zendesk_tickets`.
- The acquisition channel goes to `appsflyer_installs`.

## Reads and write-back

- The store reads customers, subscriptions, payments and usage through the views `v_customers`, `v_subscriptions`, `v_payments` and `v_usage`. Each view selects the JSON projection of its domain object, so the policy, tests and API see the same shapes as before.
- Actions write back in one SQL transaction per action: refunds update `stripe_charges` (`amount_refunded`, `refunded`) and insert into `stripe_refunds`; cancel, pause, discount and extend change `stripe_subscriptions` and `app_subscriptions`; offers update `app_users`. A failed write rolls back both the SQL rows and the in-memory change.
- A refund row has the id `<ticket>:<action>`, so a second refund for the same action cannot be inserted.

## Known gap

The views copy the JSON projections, not columns from the provider tables. The provider tables record every action, but a manual edit to them is not read back by the store until the projection is updated. Assembling the domain objects from the provider columns is the next step.
