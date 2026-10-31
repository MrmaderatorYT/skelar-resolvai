package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.*;
import com.skelar.defuse.plugins.billing.MockBillingOperations;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class OfferDiscountHandler implements ActionHandler {
    private final MockBillingOperations ops;
    public OfferDiscountHandler(MockBillingOperations ops) { this.ops=ops; }
    public String action() { return "offer_discount"; }
    public boolean movesMoney() { return false; }
    public Map<String,Object> run(ActionContext ctx) { return ops.recordOffer(ctx.customer(), action()); }
}
