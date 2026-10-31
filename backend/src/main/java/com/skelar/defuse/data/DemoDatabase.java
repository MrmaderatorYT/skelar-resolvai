package com.skelar.defuse.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.model.domain.*;
import com.skelar.defuse.store.DataLoader;
import com.skelar.defuse.store.InMemoryStore;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import javax.sql.DataSource;
import java.sql.SQLException;

/** Seeds synthetic data into SQL and projects current demo state to provider-shaped tables. */
@Component
@DependsOnDatabaseInitialization
public class DemoDatabase {
    private final JdbcTemplate jdbc;
    private final DataLoader loader;
    private final ObjectMapper mapper;
    private final DataSource dataSource;
    private final boolean allowExternalReseed;
    public DemoDatabase(JdbcTemplate jdbc, DataLoader loader, ObjectMapper mapper, DataSource dataSource,
                        @Value("${DEFUSE_ALLOW_DEMO_RESEED:false}") boolean allowExternalReseed) {
        this.jdbc = jdbc; this.loader = loader; this.mapper = mapper; this.dataSource = dataSource; this.allowExternalReseed = allowExternalReseed;
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode demo data", e); } }
    private long cents(double value) { return BigDecimal.valueOf(value).movePointRight(2).longValueExact(); }
    private static final List<String> TABLES = List.of("stripe_refunds", "stripe_disputes", "stripe_charges", "stripe_subscriptions", "stripe_customers", "appstore_transactions", "appstore_notifications", "play_subscriptions", "rc_entitlements", "rc_subscribers", "zendesk_tickets", "appsflyer_installs", "app_history", "app_payments", "app_subscriptions", "app_usage", "app_users");

    /** Startup: the in-memory demo database is rebuilt; a real database is seeded only when empty and never erased. */
    @Transactional
    public void initialize() {
        if (inMemory()) { reset(); return; }
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);
        if (users == null || users == 0) seed();
    }

    /** Explicit demo reset: erases every table, then seeds. A non-memory database needs DEFUSE_ALLOW_DEMO_RESEED=true. */
    @Transactional
    public void reset() {
        if (!inMemory() && !allowExternalReseed)
            throw new IllegalStateException("Refusing to erase non-memory database without DEFUSE_ALLOW_DEMO_RESEED=true");
        for (String table : TABLES) jdbc.update("DELETE FROM " + table); // fixed internal identifiers
        seed();
    }

    private boolean inMemory() {
        try (var connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            return url != null && url.startsWith("jdbc:h2:mem:");
        } catch (SQLException e) { throw new IllegalStateException("Cannot inspect database", e); }
    }

    private void seed() {
        List<Customer> customers = loader.load("customers", new TypeReference<>() {});
        List<Subscription> subs = loader.load("subscriptions", new TypeReference<>() {});
        List<Payment> payments = loader.load("payments", new TypeReference<>() {});
        List<Usage> usage = loader.load("usage", new TypeReference<>() {});
        List<JsonNode> tickets = loader.load("tickets_eval", new TypeReference<>() {});
        List<HistoryTicket> history = loader.load("tickets_history", new TypeReference<>() {});
        Map<String, Customer> byCustomer = new HashMap<>(); customers.forEach(c -> byCustomer.put(c.id, c));
        jdbc.batchUpdate("INSERT INTO app_users VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", customers.stream().map(c -> new Object[]{c.id,c.name,c.country,c.language,c.quizGoal,c.cardFingerprint,c.offers12m,c.lastOfferAt,c.contactsLast30d,c.acquisitionChannel,c.ltvUsd,json(c)}).toList());
        jdbc.batchUpdate("INSERT INTO app_usage VALUES (?,?,?,?,?,?)", usage.stream().map(u -> new Object[]{u.customerId,u.sessionsSinceLastCharge,u.lastActiveAt,u.streakDays,u.cancelFlowAbandoned,json(u)}).toList());
        jdbc.batchUpdate("INSERT INTO app_subscriptions VALUES (?,?,?,?,?,?,?)", subs.stream().map(s -> new Object[]{s.id,s.customerId,s.processor,s.status,s.plan,s.price,json(s)}).toList());
        jdbc.batchUpdate("INSERT INTO app_payments VALUES (?,?,?,?,?,?,?,?)", payments.stream().map(p -> new Object[]{p.id,p.customerId,p.subscriptionId,p.processor,p.status,p.amount,p.createdAt,json(p)}).toList());
        var cardOwners = subs.stream().filter(s -> "card".equals(s.processor)).map(s -> s.customerId).collect(java.util.stream.Collectors.toSet());
        jdbc.batchUpdate("INSERT INTO stripe_customers VALUES (?,?,?,?)", customers.stream().filter(c -> cardOwners.contains(c.id)).map(c -> new Object[]{c.id,c.email,c.signupAt,c.id}).toList());
        jdbc.batchUpdate("INSERT INTO stripe_subscriptions VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", subs.stream().filter(s -> "card".equals(s.processor)).map(s -> new Object[]{s.id,s.customerId,s.status,null,s.currentPeriodEnd,"canceled".equals(s.status),s.canceledAt,s.plan,cents(s.price),s.plan,s.discountPercent,s.pauseUntil}).toList());
        jdbc.batchUpdate("INSERT INTO stripe_charges VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", payments.stream().filter(p -> "card".equals(p.processor)).map(p -> new Object[]{p.id,p.customerId,cents(p.amount),p.currency,p.status,p.createdAt,byCustomer.get(p.customerId).cardFingerprint,null,p.type,"refunded".equals(p.status),p.refundedAmount == null ? ("refunded".equals(p.status) ? cents(p.amount) : 0L) : cents(p.refundedAmount),"disputed".equals(p.status) || "charged_back".equals(p.status)}).toList());
        jdbc.batchUpdate("INSERT INTO stripe_refunds VALUES (?,?,?,?,?,?)", payments.stream().filter(p -> "card".equals(p.processor) && "refunded".equals(p.status)).map(p -> new Object[]{"seed_"+p.id,p.id,p.refundedAmount == null ? cents(p.amount) : cents(p.refundedAmount),"succeeded",p.createdAt,"seed"}).toList());
        jdbc.batchUpdate("INSERT INTO stripe_disputes VALUES (?,?,?,?,?,?)", payments.stream().filter(p -> "card".equals(p.processor) && ("disputed".equals(p.status) || "charged_back".equals(p.status))).map(p -> new Object[]{"seed_"+p.id,p.id,cents(p.amount),p.status,"seed",p.createdAt}).toList());
        var storeSubs = subs.stream().filter(s -> !"card".equals(s.processor)).toList();
        var storeOwners = storeSubs.stream().map(s -> s.customerId).collect(java.util.stream.Collectors.toSet());
        jdbc.batchUpdate("INSERT INTO rc_subscribers VALUES (?,?)", storeOwners.stream().map(id -> new Object[]{id,id}).toList());
        jdbc.batchUpdate("INSERT INTO rc_entitlements VALUES (?,?,?,?,?,?,?)", storeSubs.stream().map(s -> new Object[]{s.id,s.customerId,"premium",s.processor,s.currentPeriodEnd,s.plan,s.canceledAt}).toList());
        jdbc.batchUpdate("INSERT INTO appstore_transactions VALUES (?,?,?,?,?,?,?)", payments.stream().filter(p -> "app_store".equals(p.processor)).map(p -> { var s = matchingSubscription(subs,p); return new Object[]{p.id,s.id,s.plan,p.createdAt,s.currentPeriodEnd,"refunded".equals(p.status) ? p.createdAt : null,p.customerId}; }).toList());
        jdbc.batchUpdate("INSERT INTO appstore_notifications VALUES (?,?,?,?,?)", storeSubs.stream().filter(s -> "app_store".equals(s.processor) && s.canceledAt != null).map(s -> new Object[]{"seed_"+s.id,"DID_CHANGE_RENEWAL_STATUS","AUTO_RENEW_DISABLED",s.id,s.canceledAt}).toList());
        jdbc.batchUpdate("INSERT INTO play_subscriptions VALUES (?,?,?,?,?,?)", storeSubs.stream().filter(s -> "google_play".equals(s.processor)).map(s -> new Object[]{s.id,s.status,s.plan,s.currentPeriodEnd,null,s.customerId}).toList());
        jdbc.batchUpdate("INSERT INTO appsflyer_installs VALUES (?,?,?,?,?,?)", customers.stream().map(c -> new Object[]{"install_"+c.id,c.id,c.acquisitionChannel,null,c.signupAt,c.platform}).toList());
        jdbc.batchUpdate("INSERT INTO zendesk_tickets VALUES (?,?,?,?,?,?,?,?,?,?)", tickets.stream().map(t -> new Object[]{t.path("id").asText(),t.path("customer_id").asText(),"Support request",t.path("text").asText(),"open",t.path("gold").path("priority").asText(null),json(t.path("gold").path("tags")),t.path("created_at").asText(),t.path("channel").asText(),json(t)}).toList());
        jdbc.batchUpdate("INSERT INTO app_history VALUES (?,?,?,?)", history.stream().map(h -> new Object[]{h.id,h.customerId,h.rootCause,h.refundAmount}).toList());
    }
    private Subscription matchingSubscription(List<Subscription> subs, Payment p) {
        return subs.stream().filter(s -> s.customerId.equals(p.customerId) && s.processor.equals(p.processor)
            && (p.subscriptionId == null || p.subscriptionId.equals(s.id))).findFirst().orElseThrow(() -> new IllegalStateException("No subscription for payment " + p.id));
    }
    private <T> List<T> read(String view, Class<T> type) {
        return jdbc.query("SELECT raw_json FROM " + view + " ORDER BY " + (view.equals("v_usage") ? "customer_id" : "id"), (rs,n) -> {
            try { return mapper.readValue(rs.getString(1),type); } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid SQL projection " + view,e); }
        });
    }
    public List<Customer> customers() { return read("v_customers",Customer.class); }
    public List<Subscription> subscriptions() { return read("v_subscriptions",Subscription.class); }
    public List<Payment> payments() { return read("v_payments",Payment.class); }
    public List<Usage> usage() { return read("v_usage",Usage.class); }
    public List<Ticket> tickets() { return jdbc.query("SELECT raw_json FROM zendesk_tickets ORDER BY id", (rs,n) -> { try { return mapper.readValue(rs.getString(1),Ticket.class); } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid ticket projection",e); } }); }

    public void insertTicket(Ticket ticket) {
        var raw = mapper.valueToTree(ticket);
        jdbc.update("INSERT INTO zendesk_tickets (id,requester_id,subject,description,status,priority,tags,created_at,channel,raw_json) VALUES (?,?,?,?,?,?,?,?,?,?)",
            ticket.id,ticket.customerId,"Support request",ticket.text,ticket.status,null,"[]",ticket.createdAt,ticket.channel,json(raw));
    }

    /** Updates all touched records atomically in SQL. A failed write is rolled back. */
    @Transactional
    public void persistAction(String ticketId, String action, Customer customer, Subscription subscription, Payment payment, double refundAmount) {
        boolean refund = action.startsWith("refund_");
        boolean offer = action.equals("offer_discount") || action.equals("offer_pause");
        boolean subscriptionChange = Set.of("cancel", "pause", "apply_discount", "extend_access_7d").contains(action);
        if (refund) {
            if (payment == null || !customer.id.equals(payment.customerId) || !"card".equals(payment.processor))
                throw new IllegalStateException("Refund requires a linked card payment");
            long refundCents = cents(refundAmount);
            // Stripe keeps charge.status succeeded; the refund lives in amount_refunded and the refunded flag.
            int charge = jdbc.update("UPDATE stripe_charges SET amount_refunded=amount_refunded+?, refunded=(amount_refunded+? >= amount) WHERE id=? AND customer=? AND status=? AND amount_refunded+? <= amount",
                    refundCents, refundCents, payment.id, customer.id, "succeeded", refundCents);
            int projection = jdbc.update("UPDATE app_payments SET status=?, raw_json=? WHERE id=? AND customer_id=? AND status=?",
                    payment.status, json(payment), payment.id, customer.id, "succeeded");
            if (charge != 1 || projection != 1) throw new IllegalStateException("Payment changed before refund");
            jdbc.update("INSERT INTO stripe_refunds (id,charge,amount,status,created,reason) VALUES (?,?,?,?,?,?)",
                    ticketId+":"+action, payment.id, refundCents, "succeeded", InMemoryStore.NOW_ISO,
                    action.equals("refund_duplicate") ? "duplicate" : "requested_by_customer");
        }
        if (refund || offer) {
            if (jdbc.update("UPDATE app_users SET offers_12m=?, last_offer_at=?, ltv_usd=?, raw_json=? WHERE id=?",
                    customer.offers12m,customer.lastOfferAt,customer.ltvUsd,json(customer),customer.id) != 1)
                throw new IllegalStateException("Customer missing from SQL projection");
        }
        if (subscriptionChange) {
            if (subscription == null || !customer.id.equals(subscription.customerId) || !"card".equals(subscription.processor))
                throw new IllegalStateException("Subscription requires a linked card account");
            if (jdbc.update("UPDATE app_subscriptions SET status=?, price=?, raw_json=? WHERE id=? AND customer_id=?",
                    subscription.status,subscription.price,json(subscription),subscription.id,customer.id) != 1)
                throw new IllegalStateException("Subscription missing from SQL projection");
            if (jdbc.update("UPDATE stripe_subscriptions SET status=?, current_period_end=?, cancel_at_period_end=?, canceled_at=?, discount_percent=?, pause_collection_resumes_at=? WHERE id=? AND customer=?",
                    subscription.status,subscription.currentPeriodEnd,"canceled".equals(subscription.status),subscription.canceledAt,subscription.discountPercent,subscription.pauseUntil,subscription.id,customer.id) != 1)
                throw new IllegalStateException("Card subscription missing from SQL");
        }
    }
}
