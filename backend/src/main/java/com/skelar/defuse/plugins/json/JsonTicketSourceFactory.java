package com.skelar.defuse.plugins.json;
import com.skelar.defuse.spi.*;
import com.skelar.defuse.store.InMemoryStore;
import com.skelar.defuse.model.domain.Ticket;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class JsonTicketSourceFactory implements ConnectorFactory<TicketSource> {
    private final InMemoryStore store; private final ObjectMapper mapper;
    public JsonTicketSourceFactory(InMemoryStore store,ObjectMapper mapper) { this.store=store; this.mapper=mapper; }
    public String type() { return "json"; }
    public Class<TicketSource> kind() { return TicketSource.class; }
    public TicketSource create(Map<String,String> settings) { return ()->store.tickets.values().stream()
        .filter(t->"open".equals(t.status)).map(t->mapper.convertValue(t,Ticket.class)).toList(); }
}
