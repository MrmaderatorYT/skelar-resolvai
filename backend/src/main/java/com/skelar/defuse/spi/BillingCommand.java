package com.skelar.defuse.spi;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
/** The provider idempotency key models https://docs.stripe.com/api/idempotent_requests. */
public record BillingCommand(String ticketId, String action, CustomerContext customer,
                             PolicyDecision decision, String idempotencyKey) {
    public BillingCommand {
        if (!(ticketId + ":" + action).equals(idempotencyKey))
            throw new IllegalArgumentException("Billing command requires ticketId:action idempotency key");
    }
}
