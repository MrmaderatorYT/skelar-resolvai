package com.skelar.defuse.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final SessionService sessions;
    public AuthController(SessionService sessions) { this.sessions = sessions; }
    @PostMapping("/login") public SessionService.LoginResult login(@Valid @RequestBody Login input) {
        var result = sessions.login(input.email(), input.password());
        if (result == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials or account is not configured");
        return result;
    }
    @GetMapping("/me") public SessionService.Session me(HttpServletRequest request) { return (SessionService.Session) request.getAttribute("defuse.session"); }
    @PostMapping("/logout") public void logout(HttpServletRequest request) { sessions.logout(AuthFilter.token(request)); }
    public record Login(@NotBlank String email, @NotBlank String password) {}
}
