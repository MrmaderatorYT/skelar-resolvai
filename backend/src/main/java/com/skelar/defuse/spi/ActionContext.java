package com.skelar.defuse.spi;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
public record ActionContext(String ticketId, CustomerContext customer, PolicyDecision decision, BillingProvider provider) {
    public BillingCommand command(String action) {
        return new BillingCommand(ticketId, action, customer, decision, ticketId + ":" + action);
    }
}
