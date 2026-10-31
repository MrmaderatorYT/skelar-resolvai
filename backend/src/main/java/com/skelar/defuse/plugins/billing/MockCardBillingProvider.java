package com.skelar.defuse.plugins.billing;
import com.skelar.defuse.spi.*;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.domain.Subscription;
import com.skelar.defuse.store.InMemoryStore;
import com.skelar.defuse.service.executor.BillingException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
@Component
public class MockCardBillingProvider implements BillingProvider {
    private final InMemoryStore store; private final MockBillingOperations ops;
    public MockCardBillingProvider(InMemoryStore store, MockBillingOperations ops) { this.store=store; this.ops=ops; }
    public String processor() { return "card"; }
    public Set<Capability> capabilities() { return Set.of(Capability.values()); }
    public List<Payment> payments(String id) { return store.payments(id); }
    public Subscription subscription(String id) { return store.subscription(id); }
    public Map<String, Object> execute(BillingCommand command) {
        var ctx=command.customer(); var d=command.decision();
        // Provider-local operation translation. The core executor has no action switch.
        return switch(command.action()) {
            case "cancel" -> ops.cancel(ctx);
            case "pause" -> ops.pause(ctx);
            case "apply_discount" -> ops.applyDiscount(ctx);
            case "extend_access_7d" -> ops.extendAccess(ctx);
            case "refund_full", "refund_partial", "refund_partial_goodwill", "refund_duplicate" -> {
                String id=command.action().equals("refund_duplicate") ? ctx.duplicatePaymentId() : ctx.lastPayment().id();
                if (id == null) throw new BillingException("No duplicate payment found");
                if (!store.payment(id).customerId.equals(ctx.customerId())) throw new BillingException("Payment ownership mismatch");
                yield ops.refund(id,d.refundAmount());
            }
            default -> throw new BillingException("Unsupported mock billing command="+command.action());
        };
    }
}
