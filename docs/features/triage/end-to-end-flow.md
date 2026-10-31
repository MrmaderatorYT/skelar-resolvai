# End-to-end flow

1. Guard sees original, normalized and bounded decoded input; a hit skips classifier and reply model.
2. Gold uses seed labels; live serializes the ticket as quoted JSON in a user turn; replay restores a matching recorded analysis without billing.
3. Strict parse rejects missing/coerced/invalid features, requests one repair, then confidence0 fallback.
4. Existing policy reads unchanged feature/context records.
5. Executor resolves an action plugin and checks the actual billing provider's capabilities.
6. Reply receives successful execution results and pending actions, after execution; exceptions use templates.
7. API includes provenance; read-only replay rejects approval/offer mutations.
