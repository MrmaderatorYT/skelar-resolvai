package com.skelar.defuse;

import static org.assertj.core.api.Assertions.*;
import com.skelar.defuse.data.DemoDatabase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** A file database is not the in-memory demo: startup must keep its rows, and an explicit reset must be refused. */
// The URL goes through an environment-style placeholder: SecretsValidator rejects literal datasource values from any source except the bundled defaults.
@SpringBootTest(properties = {"DURABLE_TEST_URL=jdbc:h2:file:./build/durable-test/defuse;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.url=${DURABLE_TEST_URL}"})
class DurableDatabaseTest {
    @Autowired DemoDatabase database;
    @Autowired JdbcTemplate jdbc;

    @Test void startupNeverErasesANonMemoryDatabase() {
        String marker = "durable_" + UUID.randomUUID();
        jdbc.update("INSERT INTO stripe_refunds (id,charge,amount,status,created,reason) VALUES (?,?,?,?,?,?)",
                marker, "pay_0001_1", 1L, "succeeded", "2026-10-31T12:00:00Z", "test");
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM stripe_refunds", Integer.class);
        database.initialize();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stripe_refunds WHERE id=?", Integer.class, marker)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stripe_refunds", Integer.class)).isEqualTo(before);
        assertThatThrownBy(database::reset).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEFUSE_ALLOW_DEMO_RESEED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stripe_refunds WHERE id=?", Integer.class, marker)).isEqualTo(1);
        jdbc.update("DELETE FROM stripe_refunds WHERE id=?", marker);
    }
}
