package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.Capability;
import org.springframework.stereotype.Component;
@Component
public class DuplicateRefundHandler extends BillingActionHandler {
    public DuplicateRefundHandler() { super("refund_duplicate", Capability.REFUND, true); }
}
