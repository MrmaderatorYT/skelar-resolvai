package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.Capability;
import org.springframework.stereotype.Component;
@Component
public class RefundFullHandler extends BillingActionHandler {
    public RefundFullHandler() { super("refund_full", Capability.REFUND, true); }
}
