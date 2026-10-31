package com.skelar.defuse.plugins.json;
import com.skelar.defuse.spi.*;
import com.skelar.defuse.store.InMemoryStore;
import com.skelar.defuse.model.domain.Usage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class JsonEngagementSourceFactory implements ConnectorFactory<EngagementSource> {
    private final InMemoryStore store; private final ObjectMapper mapper;
    public JsonEngagementSourceFactory(InMemoryStore store,ObjectMapper mapper) { this.store=store; this.mapper=mapper; }
    public String type() { return "json"; }
    public Class<EngagementSource> kind() { return EngagementSource.class; }
    public EngagementSource create(Map<String,String> settings) { return id->mapper.convertValue(store.usage(id),Usage.class); }
}
