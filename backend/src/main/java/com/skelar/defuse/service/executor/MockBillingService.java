package com.skelar.defuse.service.executor;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
import com.skelar.defuse.registry.ActionRegistry;
import com.skelar.defuse.registry.BillingRegistry;
import com.skelar.defuse.spi.ActionContext;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
/** Compatibility facade. Action behavior lives in plugins, not a core switch. */
@Service
public class MockBillingService {
    public static final Set<String> MONEY_ACTIONS = Set.of("refund_full","refund_partial","refund_partial_goodwill","refund_duplicate");
    public static final Set<String> SAFE_ACTIONS = Set.of("evidence_pack","store_refund_instructions","store_cancel_instructions","reply_only","ask_clarification");
    private final ActionRegistry actions; private final BillingRegistry billing;
    public MockBillingService(ActionRegistry actions, BillingRegistry billing) { this.actions=actions; this.billing=billing; }
    public Map<String,Object> run(String action, CustomerContext ctx, PolicyDecision d, String ticketId) {
        return actions.forAction(action).run(new ActionContext(ticketId,ctx,d,billing.forProcessor(ctx.billingProcessor())));
    }
}
