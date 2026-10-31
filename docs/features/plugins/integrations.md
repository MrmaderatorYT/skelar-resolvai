# Integrations and sources

Spring Boot 3.3 @ConfigurationProperties + @Validated: https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html . Types are bound at startup; raw password/credential-bearing DSNs require an environment placeholder, never a literal committed secret. Validation errors name the setting, not its value.

Stripe idempotency model: https://docs.stripe.com/api/idempotent_requests . The mock passes ticketId:action to providers and retains the existing in-memory execution ledger. This is not durable cross-process payment idempotency; a production connector must use provider idempotency and atomic payment-state validation.

All integrations in this MVP are local JSON/mock. Postgres/Firebase/Stripe live connectors are not implemented and are not advertised as working.
