package com.skelar.defuse.data;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only SQL over the synthetic seed. All supplied filters are bound parameters. */
@Repository
public class AnalyticsRepository {
    private final JdbcTemplate jdbc;
    public AnalyticsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Map<String, Object>> segments() { return segments(null); }
    public List<Map<String, Object>> segments(String acquisitionChannel) {
        return jdbc.query("""
                SELECT u.acquisition_channel, s.processor, h.root_cause, COUNT(*) AS tickets, SUM(h.refund_amount) AS refunds_usd
                FROM app_history h JOIN app_users u ON u.id = h.customer_id
                JOIN (SELECT customer_id, CASE WHEN COUNT(DISTINCT processor) = 1 THEN MAX(processor) ELSE 'multiple' END AS processor
                      FROM app_subscriptions GROUP BY customer_id) s ON s.customer_id = u.id
                WHERE (CAST(? AS VARCHAR(255)) IS NULL OR u.acquisition_channel = CAST(? AS VARCHAR(255)))
                GROUP BY u.acquisition_channel, s.processor, h.root_cause
                ORDER BY COUNT(*) DESC, u.acquisition_channel, s.processor, h.root_cause
                """, (rs,n) -> Map.<String,Object>of("acquisition_channel",rs.getString(1) == null ? "unknown" : rs.getString(1),
                        "payment_rail",rs.getString(2),"root_cause",rs.getString(3),"tickets",rs.getLong(4),"refunds_usd",rs.getBigDecimal(5)), acquisitionChannel, acquisitionChannel);
    }
    public List<Map<String, Object>> railMix() {
        return jdbc.query("SELECT processor, COUNT(*) FROM app_subscriptions WHERE status = ? GROUP BY processor ORDER BY processor",
                (rs,n) -> Map.<String,Object>of("payment_rail",rs.getString(1),"active_subscriptions",rs.getLong(2)), "active");
    }
    public List<Map<String, Object>> multiRailCustomers() {
        return jdbc.query("SELECT customer_id, COUNT(DISTINCT processor) FROM app_subscriptions WHERE status = ? GROUP BY customer_id HAVING COUNT(DISTINCT processor) >= ? ORDER BY customer_id",
                (rs,n) -> Map.<String,Object>of("customer_id",rs.getString(1),"active_rails",rs.getLong(2)), "active", 2);
    }
}
