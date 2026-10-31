package com.skelar.defuse.plugins.actions;
import com.skelar.defuse.spi.*;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class ReplyOnlyHandler implements ActionHandler {
    public String action() { return "reply_only"; }
    public boolean movesMoney() { return false; }
    public Map<String,Object> run(ActionContext ctx) { return Map.of("noop",true); }
}
