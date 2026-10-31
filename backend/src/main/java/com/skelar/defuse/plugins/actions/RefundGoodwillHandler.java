package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.Capability;
import org.springframework.stereotype.Component;
@Component
public class RefundGoodwillHandler extends BillingActionHandler {
    public RefundGoodwillHandler() { super("refund_partial_goodwill", Capability.REFUND, true); }
}
