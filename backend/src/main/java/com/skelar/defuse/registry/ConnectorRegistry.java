package com.skelar.defuse.registry;
import com.skelar.defuse.spi.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class ConnectorRegistry {
    private final Map<String, Object> sources = new LinkedHashMap<>();
    public ConnectorRegistry(List<ConnectorFactory<?>> factories, PluginProperties cfg, SecretsValidator secrets) {
        Map<String, ConnectorFactory<?>> index = new LinkedHashMap<>();
        for (ConnectorFactory<?> f : factories) if (index.putIfAbsent(f.kind().getName() + ":" + f.type(), f) != null)
            throw new IllegalStateException("Duplicate ConnectorFactory for kind=" + f.kind().getSimpleName() + ", type=" + f.type());
        Map<String, Class<?>> kinds = Map.of("customers", CustomerSource.class, "engagement", EngagementSource.class, "tickets", TicketSource.class);
        cfg.getDatasources().forEach((name, settings) -> {
            Class<?> kind = kinds.get(name);
            if (kind == null) throw new IllegalStateException("Unknown datasource name=" + name);
            String type = settings.get("type");
            ConnectorFactory<?> factory = index.get(kind.getName() + ":" + type);
            if (factory == null) throw new IllegalStateException("No ConnectorFactory for " + name + ", type=" + type);
            sources.put(name, factory.create(Map.copyOf(settings)));
        });
        for (String required : kinds.keySet()) if (!sources.containsKey(required))
            throw new IllegalStateException("Missing datasource=" + required);
        for (var billing : cfg.getBilling()) {
            String type = billing.get("type"), processor = billing.get("processor");
            if (!("mock".equals(type) && "card".equals(processor))
                    && !("store".equals(type) && List.of("app_store", "google_play").contains(processor)))
                throw new IllegalStateException("Unknown billing type=" + type + ", processor=" + processor);
        }
        if (cfg.getBilling().stream().map(b -> b.get("processor")).distinct().count() != cfg.getBilling().size())
            throw new IllegalStateException("Duplicate billing processor in configuration");
    }
    public <T> T get(String name, Class<T> kind) {
        Object source = sources.get(name);
        if (!kind.isInstance(source)) throw new IllegalStateException("No datasource " + name + " of kind=" + kind.getSimpleName());
        return kind.cast(source);
    }
}
