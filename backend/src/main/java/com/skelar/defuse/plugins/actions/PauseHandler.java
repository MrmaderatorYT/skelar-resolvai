package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.Capability;
import org.springframework.stereotype.Component;
@Component
public class PauseHandler extends BillingActionHandler {
    public PauseHandler() { super("pause", Capability.PAUSE, false); }
}
