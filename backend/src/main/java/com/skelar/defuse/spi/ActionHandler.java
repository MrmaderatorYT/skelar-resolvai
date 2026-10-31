package com.skelar.defuse.spi;
import java.util.Map;
public interface ActionHandler {
    String action();
    boolean movesMoney();
    default Capability requiredCapability() { return null; }
    Map<String, Object> run(ActionContext context);
}
