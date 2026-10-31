package com.skelar.defuse.registry;
import com.skelar.defuse.spi.BillingProvider;
import com.skelar.defuse.service.executor.BillingException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class BillingRegistry {
    private final Map<String, BillingProvider> providers = new LinkedHashMap<>();
    public BillingRegistry(List<BillingProvider> plugins, PluginProperties config) {
        Map<String,BillingProvider> available=new LinkedHashMap<>();
        for (BillingProvider p : plugins) if (available.putIfAbsent(p.processor(), p) != null)
            throw new IllegalStateException("Duplicate BillingProvider for processor=" + p.processor());
        for(var configured:config.getBilling()) {
            String processor=configured.get("processor"),type=configured.get("type");
            if (!("mock".equals(type) && "card".equals(processor))
                    && !("store".equals(type) && List.of("app_store","google_play").contains(processor)))
                throw new IllegalStateException("Unknown billing type="+type+", processor="+processor);
            BillingProvider provider=available.get(processor);
            if(provider==null) throw new IllegalStateException("No BillingProvider for configured processor="+processor);
            if(providers.putIfAbsent(processor,provider)!=null) throw new IllegalStateException("Duplicate configured billing processor="+processor);
        }
    }
    public BillingProvider forProcessor(String processor) {
        BillingProvider p = providers.get(processor);
        if (p == null) throw new BillingException("No BillingProvider for processor=" + processor);
        return p;
    }
}
