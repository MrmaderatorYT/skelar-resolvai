package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.*;
import com.skelar.defuse.plugins.billing.MockBillingOperations;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class EvidencePackHandler implements ActionHandler {
    private final MockBillingOperations ops;
    public EvidencePackHandler(MockBillingOperations ops) { this.ops=ops; }
    public String action() { return "evidence_pack"; }
    public boolean movesMoney() { return false; }
    public Map<String,Object> run(ActionContext ctx) { return ops.evidencePack(ctx.customer(),ctx.ticketId()); }
}
