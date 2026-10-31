package com.skelar.defuse.service.policy;

import com.skelar.defuse.model.domain.Customer;
import com.skelar.defuse.model.domain.Payment;
import com.skelar.defuse.model.domain.Subscription;
import com.skelar.defuse.model.domain.Usage;
import com.skelar.defuse.model.dto.CustomerContext;
import com.skelar.defuse.model.dto.LastPayment;
import com.skelar.defuse.config.policy.PolicyProperties;
import com.skelar.defuse.store.InMemoryStore;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** Customer 360 for the policy: port of reference_policy.build_context(). */
@Service
public class ContextBuilderService {
    private final InMemoryStore store;
    private final PolicyProperties props;

    public ContextBuilderService(InMemoryStore store, PolicyProperties props) {
        this.store = store;
        this.props = props;
    }

    /** Whole days between iso and NOW, floored like Python's timedelta.days. */
    public static long daysAgo(String iso) {
        long seconds = Duration.between(OffsetDateTime.parse(iso), InMemoryStore.NOW).getSeconds();
        return Math.floorDiv(seconds, 86_400);
    }

    public CustomerContext build(String customerId) {
        Customer c = store.customer(customerId);
        Subscription sub = store.subscription(customerId);
        Usage u = store.usage(customerId);
        List<Payment> pays = store.payments(customerId);
        if (pays.isEmpty()) throw new IllegalStateException("no_payments: human review required");
        Payment last = pays.get(pays.size() - 1);
        List<Payment> sameDay = pays.stream()
                .filter(p -> p.status.equals("succeeded") && p.amount == last.amount
                        && p.createdAt.substring(0, 10).equals(last.createdAt.substring(0, 10)))
                .toList();
        int refunds180 = (int) pays.stream()
                .filter(p -> p.status.equals("refunded") && daysAgo(p.createdAt) <= 180).count();
        int chargebacks = (int) pays.stream().filter(p -> p.status.equals("charged_back")).count();
        int paidRenewals = (int) pays.stream().filter(p -> p.type.equals("renewal") && p.status.equals("succeeded")).count();
        int identityOffers = store.customers.stream()
                .filter(o -> c.cardFingerprint != null && !o.id.equals(customerId) && c.cardFingerprint.equals(o.cardFingerprint))
                .mapToInt(this::recentOffers).sum();
        int offers30d = (int) store.customers.stream()
                .filter(o -> o.lastOfferAt != null && daysAgo(o.lastOfferAt) <= 30).count();
        boolean holdout = Integer.parseInt(customerId.substring(customerId.lastIndexOf('_') + 1)) % 10
                == props.retention().holdoutBucket();
        return new CustomerContext(
                customerId, c.platform, sub.processor, c.ltvUsd, c.contactsLast30d, refunds180, chargebacks,
                sub.status, sub.plan, sub.price,
                new LastPayment(last.id, last.amount, last.status, daysAgo(last.createdAt)),
                sameDay.size() > 1 ? sameDay.get(sameDay.size() - 1).id : null,
                u.sessionsSinceLastCharge, u.cancelFlowAbandoned,
                c.acquisitionChannel, Math.floorDiv(daysAgo(c.signupAt), 30), paidRenewals, recentOffers(c),
                identityOffers, offers30d, holdout);
    }

    /** Offers made to this customer within the cooldown window. */
    int recentOffers(Customer c) {
        return c.lastOfferAt != null && daysAgo(c.lastOfferAt) <= props.retention().cooldownDays() ? c.offers12m : 0;
    }
}
