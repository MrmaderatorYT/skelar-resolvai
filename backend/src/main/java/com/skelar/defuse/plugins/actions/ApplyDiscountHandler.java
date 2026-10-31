package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.Capability;
import org.springframework.stereotype.Component;
@Component
public class ApplyDiscountHandler extends BillingActionHandler {
    public ApplyDiscountHandler() { super("apply_discount", Capability.DISCOUNT, true); }
}
