package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.Capability;
import org.springframework.stereotype.Component;
@Component
public class CancelHandler extends BillingActionHandler {
    public CancelHandler() { super("cancel", Capability.CANCEL, false); }
}
