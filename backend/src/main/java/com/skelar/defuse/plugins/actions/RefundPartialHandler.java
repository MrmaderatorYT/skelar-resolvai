package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.Capability;
import org.springframework.stereotype.Component;
@Component
public class RefundPartialHandler extends BillingActionHandler {
    public RefundPartialHandler() { super("refund_partial", Capability.REFUND, true); }
}
