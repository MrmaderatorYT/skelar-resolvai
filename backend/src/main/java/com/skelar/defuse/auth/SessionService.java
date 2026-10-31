package com.skelar.defuse.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Demo-only in-memory sessions. Passwords are supplied by the backend environment. */
@Service
public class SessionService {
    private final String customerPassword, adminPassword, customerId;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    public SessionService(@Value("${DEFUSE_CUSTOMER_PASSWORD:${defuse.auth.customer-password:}}") String customerPassword,
            @Value("${DEFUSE_ADMIN_PASSWORD:${defuse.auth.admin-password:}}") String adminPassword,
            @Value("${DEFUSE_CUSTOMER_ID:${defuse.auth.customer-id:cus_0001}}") String customerId) {
        this.customerPassword = customerPassword; this.adminPassword = adminPassword; this.customerId = customerId;
    }
    public LoginResult login(String email, String password) {
        String role = null, expected = "";
        if ("customer@calmly.demo".equalsIgnoreCase(email)) { role = "customer"; expected = customerPassword; }
        if ("admin@calmly.demo".equalsIgnoreCase(email)) { role = "admin"; expected = adminPassword; }
        if (role == null || expected.isBlank() || password == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8))) return null;
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(role, role.equals("customer") ? customerId : null, Instant.now().plusSeconds(8 * 3600));
        sessions.put(token, session); return new LoginResult(token, session.role(), session.customerId());
    }
    public Session get(String token) {
        if (token == null) return null;
        Session session = sessions.get(token);
        if (session != null && !session.expiresAt().isAfter(Instant.now())) { sessions.remove(token); return null; }
        return session;
    }
    public void logout(String token) { if (token != null) sessions.remove(token); }
    public record Session(String role, String customerId, Instant expiresAt) {}
    public record LoginResult(String token, String role, String customerId) {}
}
