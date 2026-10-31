# Data model

BillingCommand holds ticketId, action, CustomerContext, PolicyDecision and idempotencyKey. Key must equal ticketId:action. ActionContext also carries the selected provider. Capability is REFUND/CANCEL/PAUSE/DISCOUNT/EXTEND. A store provider has an empty set. Existing action result map fields remain unchanged. Sources return copies of mutable domain objects so callers cannot write through the source API.

Datasource path supports classpath seed resources or explicit file: paths; only JSON is implemented. Arbitrary provider/factory type names fail startup rather than silently using mock JSON.
