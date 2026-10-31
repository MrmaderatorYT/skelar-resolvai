package com.skelar.defuse.spi;
import com.skelar.defuse.config.policy.PolicyVersion;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.PolicyDecision;
/** The provider idempotency key models https://docs.stripe.com/api/idempotent_requests. idempotencyKey is the
 *  local operation record (ticketId:action); providerKey binds the remote call to merchant, processor, payment,
 *  action, amount and currency (spec H), so a changed amount can never reuse a key. */
public record BillingCommand(String ticketId, String action, CustomerContext customer,
                             PolicyDecision decision, String idempotencyKey, String providerKey) {
    public static final String MERCHANT = "calmly-demo", CURRENCY = "USD";   // one USD demo tenant (spec E4)
    public BillingCommand {
        if (!(ticketId + ":" + action).equals(idempotencyKey))
            throw new IllegalArgumentException("Billing command requires ticketId:action idempotency key");
        if (providerKey == null || providerKey.isBlank()) throw new IllegalArgumentException("Billing command requires a provider key");
    }
    public BillingCommand(String ticketId, String action, CustomerContext customer, PolicyDecision decision, String idempotencyKey) {
        this(ticketId, action, customer, decision, idempotencyKey, providerKey(action, customer, decision, idempotencyKey));
    }
    static String providerKey(String action, CustomerContext ctx, PolicyDecision d, String localKey) {
        boolean money = action != null && action.startsWith("refund_");
        String payment = !money || ctx == null ? "-" : action.equals("refund_duplicate") ? ctx.duplicatePaymentId()
                : ctx.lastPayment() == null ? null : ctx.lastPayment().id();
        long cents = money && d != null && Double.isFinite(d.refundAmount()) ? Math.round(d.refundAmount() * 100) : 0;
        String processor = ctx == null ? "-" : ctx.billingProcessor();
        return PolicyVersion.sha256(String.join("|", MERCHANT, String.valueOf(processor), String.valueOf(payment),
                String.valueOf(action), Long.toString(cents), CURRENCY, String.valueOf(localKey)));
    }
}
