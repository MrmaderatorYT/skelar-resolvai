package com.skelar.defuse.spi;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.domain.Subscription;
import java.util.List;
import java.util.Map;
import java.util.Set;
public interface BillingProvider {
    String processor();
    Set<Capability> capabilities();
    List<Payment> payments(String customerId);
    Subscription subscription(String customerId);
    Map<String, Object> execute(BillingCommand command);
}
