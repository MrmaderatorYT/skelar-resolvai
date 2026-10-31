package com.skelar.defuse.spi;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.domain.Subscription;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
public interface BillingProvider {
    String processor();
    Set<Capability> capabilities();
    List<Payment> payments(String customerId);
    Subscription subscription(String customerId);
    /** May throw BillingTimeoutException: the remote side may or may not have applied the command. */
    Map<String, Object> execute(BillingCommand command);
    /** Idempotent read of a command's outcome by providerKey, to reconcile after a timeout. Never mutates. */
    default Optional<Map<String, Object>> lookup(String providerKey) { return Optional.empty(); }
}
