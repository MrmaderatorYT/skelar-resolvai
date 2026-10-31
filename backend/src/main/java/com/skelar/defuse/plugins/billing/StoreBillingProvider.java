package com.skelar.defuse.plugins.billing;
import com.skelar.defuse.spi.*;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.domain.Subscription;
import com.skelar.defuse.store.InMemoryStore;
import com.skelar.defuse.service.executor.BillingException;
import java.util.List;
import java.util.Map;
import java.util.Set;
public class StoreBillingProvider implements BillingProvider {
    private final String processor; private final InMemoryStore store;
    public StoreBillingProvider(String processor, InMemoryStore store) { this.processor=processor; this.store=store; }
    public String processor() { return processor; }
    public Set<Capability> capabilities() { return Set.of(); }
    public List<Payment> payments(String id) { return store.payments(id); }
    public Subscription subscription(String id) { return store.subscription(id); }
    public Map<String, Object> execute(BillingCommand command) { throw new BillingException("Store billing has no write capabilities: "+processor); }
}
