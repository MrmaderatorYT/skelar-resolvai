package com.skelar.defuse.registry;
import com.skelar.defuse.spi.ActionHandler;
import com.skelar.defuse.service.executor.BillingException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class ActionRegistry {
    private final Map<String, ActionHandler> handlers = new LinkedHashMap<>();
    public ActionRegistry(List<ActionHandler> plugins) {
        for (ActionHandler h : plugins) if (handlers.putIfAbsent(h.action(), h) != null)
            throw new IllegalStateException("Duplicate ActionHandler for action=" + h.action());
    }
    public ActionHandler forAction(String action) {
        ActionHandler h = handlers.get(action);
        if (h == null) throw new BillingException("No ActionHandler for action=" + action);
        return h;
    }
}
