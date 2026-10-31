package com.skelar.defuse.plugins.json;
import com.skelar.defuse.spi.*;
import com.skelar.defuse.model.domain.Customer;
import com.skelar.defuse.store.InMemoryStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class JsonCustomerSourceFactory implements ConnectorFactory<CustomerSource> {
    private final InMemoryStore store; private final ObjectMapper mapper;
    public JsonCustomerSourceFactory(InMemoryStore store,ObjectMapper mapper) { this.store=store; this.mapper=mapper; }
    public String type() { return "json"; }
    public Class<CustomerSource> kind() { return CustomerSource.class; }
    public CustomerSource create(Map<String,String> settings) {
        return new CustomerSource() {
            public Optional<Customer> find(String id) { return store.customers.stream().filter(c->c.id.equals(id)).findFirst().map(c->mapper.convertValue(c,Customer.class)); }
            public List<String> identityCluster(String id) {
                Customer c=store.customer(id);
                return store.customers.stream().filter(x->x.id.equals(id) || c.cardFingerprint!=null && c.cardFingerprint.equals(x.cardFingerprint)).map(x->x.id).toList();
            }
        };
    }
}
