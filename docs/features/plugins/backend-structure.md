# Backend structure

SPI: BillingProvider, Capability, BillingCommand, ActionHandler, ActionContext, ConnectorFactory, CustomerSource, EngagementSource, TicketSource. Registries: ActionRegistry, BillingRegistry, ConnectorRegistry. Configuration: PluginProperties + SecretsValidator + PluginConfiguration. JSON adapters: three factories over the seed store. Billing: mock card plus two store beans. Actions: one handler per frozen action; mock mutation details are in MockBillingOperations. MockBillingService is a compatibility facade for existing callers/tests.
