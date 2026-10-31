package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.Capability;
import org.springframework.stereotype.Component;
@Component
public class ExtendAccessHandler extends BillingActionHandler {
    public ExtendAccessHandler() { super("extend_access_7d", Capability.EXTEND, false); }
}
