package com.skelar.defuse;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skelar.defuse.data.AnalyticsRepository;
import com.skelar.defuse.model.domain.*;
import com.skelar.defuse.service.TicketPipelineService;
import com.skelar.defuse.service.audit.AuditService;
import com.skelar.defuse.store.DataLoader;
import com.skelar.defuse.store.InMemoryStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DataLayerTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataLoader loader;
    @Autowired InMemoryStore store;
    @Autowired AnalyticsRepository analytics;
    @Autowired TicketPipelineService pipeline;
    @Autowired AuditService audit;
    @Autowired ObjectMapper mapper;
    @BeforeEach void reset() { store.reset(); audit.clear(); }
    @Test void seededViewsMatchOriginalDomainRecords() {
        List<Customer> customers = loader.load("customers", new TypeReference<>() {});
        List<Subscription> subs = loader.load("subscriptions", new TypeReference<>() {});
        List<Payment> payments = loader.load("payments", new TypeReference<>() {});
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users",Integer.class)).isEqualTo(customers.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM v_subscriptions",Integer.class)).isEqualTo(subs.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM v_payments",Integer.class)).isEqualTo(payments.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM zendesk_tickets",Integer.class)).isEqualTo(45);
        assertThat((com.fasterxml.jackson.databind.JsonNode) mapper.valueToTree(store.customers)).isEqualTo(mapper.valueToTree(customers));
        assertThat((com.fasterxml.jackson.databind.JsonNode) mapper.valueToTree(store.subscriptions)).isEqualTo(mapper.valueToTree(subs));
        assertThat((com.fasterxml.jackson.databind.JsonNode) mapper.valueToTree(store.payments)).isEqualTo(mapper.valueToTree(payments));
        List<Usage> usage = loader.load("usage", new TypeReference<>() {});
        assertThat((com.fasterxml.jackson.databind.JsonNode) mapper.valueToTree(store.usage)).isEqualTo(mapper.valueToTree(usage));
    }
    @Test void refundAndCancelAreWrittenOnceInSql() {
        var a = pipeline.analyze("T01", false);
        assertThat(a.status).isEqualTo("resolved");
        assertThat(jdbc.queryForObject("SELECT amount FROM stripe_refunds WHERE id=?",Long.class,"T01:refund_full")).isEqualTo(4999L);
        assertThat(jdbc.queryForObject("SELECT canceled_at FROM stripe_subscriptions WHERE customer=?",String.class,"cus_0001")).isNotBlank();
        pipeline.analyze("T01", true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stripe_refunds WHERE id=?",Integer.class,"T01:refund_full")).isEqualTo(1);
    }
    @Test void sqlAnalyticsUsesBoundFiltersAndRecognizesMultipleActiveRails() {
        assertThat(analytics.segments()).isNotEmpty();
        assertThat(analytics.segments("' OR '1'='1")).isEmpty();
        assertThat(analytics.railMix()).isNotEmpty();
        jdbc.update("UPDATE app_subscriptions SET status=? WHERE id=?", "active", "sub_0195_store");
        assertThat(analytics.multiRailCustomers()).anyMatch(row -> row.get("customer_id").equals("cus_0195"));
    }
    @Test void failedSqlRefundRestoresInMemoryPaymentAndCustomer() {
        var amount = store.customer("cus_0001").ltvUsd;
        jdbc.update("INSERT INTO stripe_refunds (id,charge,amount,status,created,reason) VALUES (?,?,?,?,?,?)", "T01:refund_full", "placeholder", 1, "succeeded", "seed", "test");
        var a = pipeline.analyze("T01", false);
        assertThat(a.status).isEqualTo("escalated");
        assertThat(store.payment(a.context.lastPayment().id()).status).isEqualTo("succeeded");
        assertThat(store.customer("cus_0001").ltvUsd).isEqualTo(amount);
        assertThat(jdbc.queryForObject("SELECT status FROM stripe_charges WHERE id=?",String.class,a.context.lastPayment().id())).isEqualTo("succeeded");
    }
}
