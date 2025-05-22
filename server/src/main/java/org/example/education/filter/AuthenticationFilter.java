package org.example.education.filter;

import org.example.education.model.UserType; // Убедитесь, что этот импорт есть
import org.example.education.util.JsonUtil;
import org.example.education.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.Collections;
import java.util.Optional;

public class AuthenticationFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    private final JwtUtil jwtUtil;

    public AuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        // 1. Пропускаем OPTIONS запросы без проверки токена для CORS pre-flight.
        // Обработчик options("/*", ...) в App.java должен сам корректно ответить.
        if (request.requestMethod().equalsIgnoreCase("OPTIONS")) {
            auditLogger.debug("OPTIONS request to {} - passing through AuthenticationFilter for CORS pre-flight.", request.pathInfo());
            return; // Позволяем следующему обработчику (options() в App.java) сработать
        }

        // 2. Определяем публичные эндпоинты, не требующие аутентификации.
        // Например, вход в систему и регистрация нового студента.
        boolean isPublicEndpoint = false;
        if (request.pathInfo().equals("/api/auth/login") && request.requestMethod().equalsIgnoreCase("POST")) {
            isPublicEndpoint = true;
        } else if (request.pathInfo().equals("/api/students") && request.requestMethod().equalsIgnoreCase("POST")) {
            // Регистрация нового студента также публична
            isPublicEndpoint = true;
        }
        // Можно добавить другие публичные эндпоинты, например, GET /api/courses для всех

        if (isPublicEndpoint) {
            auditLogger.info("Public access granted to {} {}", request.requestMethod(), request.pathInfo());
            return; // Пропускаем без проверки токена
        }

        // 3. Для всех остальных эндпоинтов требуется аутентификация.
        String authHeader = request.headers("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Missing or invalid Authorization header for {} {}", request.requestMethod(), request.pathInfo());
            auditLogger.warn("Unauthorized access attempt (missing/invalid Bearer token) to {} {} from IP {}",
                    request.requestMethod(), request.pathInfo(), request.ip());
            Spark.halt(401, JsonUtil.toJson(Collections.singletonMap("error", "Unauthorized: Missing or invalid token")));
            return;
        }

        String token = authHeader.substring(7); // Удаляем "Bearer "
        Optional<Claims> claimsOptional = jwtUtil.validateTokenAndGetClaims(token);

        if (claimsOptional.isEmpty()) {
            logger.warn("Invalid or expired JWT token provided for {} {}", request.requestMethod(), request.pathInfo());
            String tokenSnippet = token.length() > 10 ? token.substring(0, 10) + "..." : token;
            auditLogger.warn("Unauthorized access attempt (invalid/expired JWT token) to {} {} from IP {}. Token: {}",
                    request.requestMethod(), request.pathInfo(), request.ip(), tokenSnippet);
            Spark.halt(401, JsonUtil.toJson(Collections.singletonMap("error", "Unauthorized: Invalid or expired token")));
            return;
        }

        // 4. Токен валиден, извлекаем информацию о пользователе и добавляем в атрибуты запроса.
        Claims claims = claimsOptional.get();
        try {
            request.attribute("userId", jwtUtil.getUserIdFromClaims(claims));
            request.attribute("userEmail", jwtUtil.getEmailFromClaims(claims));

            UserType userType = jwtUtil.getUserTypeFromClaims(claims);
            String userRole = jwtUtil.getRoleFromClaims(claims);

            if (userType == null || userRole == null) {
                logger.error("Critical: UserType or UserRole missing or invalid in a valid JWT for token: {}", token.substring(0,10)+"...");
                auditLogger.error("Critical error: UserType/Role missing in JWT. UserID: {}, Email: {}. Path: {} {}",
                        request.attribute("userId"), request.attribute("userEmail"), request.requestMethod(), request.pathInfo());
                Spark.halt(500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error: user identity corrupted in token.")));
                return;
            }

            request.attribute("userType", userType);
            request.attribute("userRole", userRole);

            auditLogger.info("Authenticated access by User ID: {}, Email: {}, Type: {}, Role: {} to {} {} from IP {}",
                    request.attribute("userId"), request.attribute("userEmail"),
                    request.attribute("userType"), request.attribute("userRole"),
                    request.requestMethod(), request.pathInfo(), request.ip());
        } catch (Exception e) {
            // Эта ошибка может возникнуть, если claims не содержат ожидаемых полей,
            // несмотря на то, что токен прошел базовую валидацию (подпись, срок действия).
            logger.error("Error extracting claims from a validated JWT. Token: {}", token.substring(0,10)+"...", e);
            auditLogger.error("Error processing claims for {} {} from IP {}. Exception: {}",
                    request.requestMethod(), request.pathInfo(), request.ip(), e.getMessage());
            Spark.halt(500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error: error processing user identity.")));
        }
    }
}