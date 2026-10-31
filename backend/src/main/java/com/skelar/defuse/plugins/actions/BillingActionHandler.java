package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.*;
import java.util.Map;
abstract class BillingActionHandler implements ActionHandler {
    private final String action; private final Capability capability; private final boolean money;
    BillingActionHandler(String action, Capability capability, boolean money) { this.action=action; this.capability=capability; this.money=money; }
    public String action() { return action; }
    public boolean movesMoney() { return money; }
    public Capability requiredCapability() { return capability; }
    public Map<String,Object> run(ActionContext ctx) { return ctx.provider().execute(ctx.command(action)); }
}
