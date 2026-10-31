package com.skelar.defuse.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final boolean enabled;
    public AuthFilter(SessionService sessions, @Value("${defuse.auth.enabled:true}") boolean enabled) { this.sessions = sessions; this.enabled = enabled; }
    public static String token(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7) : null;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!enabled || !path.startsWith("/api/") || path.equals("/api/auth/login") || path.equals("/api/health") || request.getMethod().equals("OPTIONS")) { chain.doFilter(request, response); return; }
        var session = sessions.get(token(request));
        if (session == null) { response.sendError(401, "Authentication required"); return; }
        boolean customerEndpoint = path.equals("/api/auth/me") || path.equals("/api/auth/logout") || path.equals("/api/chat") || path.equals("/api/chat/suggestions") || path.matches("/api/chat/[^/]+/offer/(accept|decline)");
        if (session.role().equals("customer") && !customerEndpoint) { response.sendError(403, "Administrator access required"); return; }
        request.setAttribute("defuse.session", session);
        chain.doFilter(request, response);
    }
}
