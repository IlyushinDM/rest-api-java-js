package org.example.education.controller;

import org.example.education.model.Session;
import org.example.education.model.UserCredentials;
import org.example.education.service.AuthService;
import org.example.education.util.JsonUtil;
// import com.example.education.util.JwtUtil; // JwtUtil используется через AuthService
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Collections;
import java.util.Optional;

import static spark.Spark.halt;
import static spark.Spark.post;

public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private final AuthService authService;
    // private final JwtUtil jwtUtil; // Можно удалить, если вся работа с JWT в AuthService

    public AuthController(AuthService authService /*, JwtUtil jwtUtil */) { // JwtUtil теперь в AuthService
        this.authService = authService;
        // this.jwtUtil = jwtUtil;
        setupRoutes();
    }

    private void setupRoutes() {
        post("/api/auth/login", this::login, JsonUtil.jsonResponseTransformer());
        post("/api/auth/logout", this::logout, JsonUtil.jsonResponseTransformer());
    }

    private Object login(Request request, Response response) {
        auditLogger.info("Login attempt from IP: {}", request.ip());
        response.type("application/json");
        UserCredentials credentials;
        try {
            credentials = JsonUtil.fromJson(request.body(), UserCredentials.class);
        } catch (Exception e) {
            logger.warn("Failed to parse login request body", e);
            auditLogger.warn("Login failed (bad request) from IP: {}. Error: {}", request.ip(), e.getMessage());
            halt(400, JsonUtil.toJson(Collections.singletonMap("error", "Bad request: " + e.getMessage())));
            return null;
        }

        Optional<Session> sessionOpt = authService.login(credentials); // AuthService теперь сам генерирует токен и сессию
        if (sessionOpt.isPresent()) {
            auditLogger.info("Login successful for user: {}. IP: {}", credentials.getEmail(), request.ip());
            response.status(200);
            return sessionOpt.get();
        } else {
            auditLogger.warn("Login failed for user: {}. IP: {}", credentials.getEmail(), request.ip());
            response.status(401); // Unauthorized
            return Collections.singletonMap("error", "Invalid email or password");
        }
    }

    private Object logout(Request request, Response response) {
        String authHeader = request.headers("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        authService.logout(token); // AuthService.logout может логировать, но для JWT ничего не делает с токеном

        Integer userId = request.attribute("userId"); // Это будет доступно, если фильтр аутентификации прошел до этого
        String userEmail = request.attribute("userEmail");

        auditLogger.info("Logout request processed for user ID: {}, Email: {}. Client should clear token.", userId, userEmail);
        response.status(200);
        return Collections.singletonMap("message", "Logged out successfully. Please clear your token on the client-side.");
    }
}