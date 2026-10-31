package com.skelar.defuse.spi;
import com.skelar.defuse.model.domain.Customer;
import java.util.List;
import java.util.Optional;
public interface CustomerSource {
    Optional<Customer> find(String customerId);
    List<String> identityCluster(String customerId);
}
