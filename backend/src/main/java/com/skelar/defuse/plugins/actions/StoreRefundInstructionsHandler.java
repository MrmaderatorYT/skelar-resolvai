package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.*;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class StoreRefundInstructionsHandler implements ActionHandler {
    public String action() { return "store_refund_instructions"; }
    public boolean movesMoney() { return false; }
    public Map<String,Object> run(ActionContext ctx) { return Map.of("noop",true); }
}
