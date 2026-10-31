# End-to-end flow

1. Bind defuse.datasources / defuse.billing and validate raw secret configuration.
2. Discover Spring beans and build unique typed registries; unknown type fails startup.
3. JSON DataLoader reads configured classpath: or file: seed paths; factories wrap the store with read-only snapshots.
4. Existing policy computes the frozen decision.
5. Executor resolves ActionHandler and selects the actual payment processor for refunds.
6. Required capability must exist; refusal/exception is audited and escalates the ticket.
7. Handler invokes provider with ticketId:action key, or a safe read-only/noop operation. The compatibility facade has no action switch.
