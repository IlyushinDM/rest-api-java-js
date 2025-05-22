package org.example.education.controller;

import org.example.education.model.Course;
import org.example.education.model.UserType; // Импорт UserType
import org.example.education.service.CourseService;
import org.example.education.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Collections;
import java.util.Optional;

import static spark.Spark.*;

public class CourseController {
    private static final Logger logger = LoggerFactory.getLogger(CourseController.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
        setupRoutes();
    }

    private void setupRoutes() {
        // CRUD операции с курсами требуют прав администратора
        post("/api/courses", this::createCourse, JsonUtil.jsonResponseTransformer());
        put("/api/courses/:id", this::updateCourse, JsonUtil.jsonResponseTransformer());
        delete("/api/courses/:id", this::deleteCourse, JsonUtil.jsonResponseTransformer());

        // Получение информации о курсах доступно всем аутентифицированным пользователям
        get("/api/courses", this::getAllCourses, JsonUtil.jsonResponseTransformer());
        get("/api/courses/:id", this::getCourseById, JsonUtil.jsonResponseTransformer());
    }

    private boolean isAdmin(Request request) {
        UserType userType = request.attribute("userType");
        String userRole = request.attribute("userRole");
        return userType == UserType.EMPLOYEE && "ADMIN".equals(userRole);
    }

    private Object createCourse(Request request, Response response) {
        response.type("application/json");
        if (!isAdmin(request)) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to create course.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }
        // ... (остальная логика создания курса как была, но теперь с проверкой прав выше) ...
        Integer requestingUserId = request.attribute("userId"); // Для логирования
        auditLogger.info("Admin User ID {} attempting to create course. Request body: {}", requestingUserId, request.body().substring(0, Math.min(request.body().length(), 200)));

        try {
            Course course = JsonUtil.fromJson(request.body(), Course.class);
            if (course.getCourseName() == null || course.getCourseName().trim().isEmpty()) {
                auditLogger.warn("Course creation failed (Bad Request - missing name) by Admin User ID {}.", requestingUserId);
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Course name is required.")));
            }

            Course createdCourse = courseService.createCourse(course);
            response.status(HttpStatus.CREATED_201);
            auditLogger.info("Course created successfully by Admin User ID {}: ID {}, Name {}", requestingUserId, createdCourse.getCourseId(), createdCourse.getCourseName());
            return createdCourse;
        } catch (RuntimeException e) { // Ловим RuntimeException
            if (e.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                logger.warn("Failed to parse create course request body by Admin User ID {}: {}", requestingUserId, e.getMessage());
                auditLogger.warn("Course creation failed (bad JSON request) by Admin User ID {}: {}", requestingUserId, e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Bad request: Invalid JSON format. " + e.getMessage())));
            } else if (e instanceof IllegalArgumentException) {
                logger.warn("Invalid data for creating course by Admin User ID {}: {}", requestingUserId, e.getMessage());
                auditLogger.warn("Course creation failed (Bad Request) by Admin User ID {}: {}", requestingUserId, e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            } else {
                logger.error("Error creating course by Admin User ID {}: {}", requestingUserId, e.getMessage(), e);
                auditLogger.error("Course creation failed (Server Error) by Admin User ID {}: {}", requestingUserId, e.getMessage());
                halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Could not create course: " + e.getMessage())));
            }
        }
        return null;
    }

    private Object getAllCourses(Request request, Response response) {
        response.type("application/json");
        // Доступно всем аутентифицированным пользователям
        auditLogger.info("User ID {} (Type: {}, Role: {}) requesting all courses.",
                request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
        return courseService.getAllCourses();
    }

    private Object getCourseById(Request request, Response response) {
        response.type("application/json");
        // Доступно всем аутентифицированным пользователям
        int courseId;
        try {
            courseId = Integer.parseInt(request.params(":id"));
        } catch (NumberFormatException e) {
            auditLogger.warn("Invalid course ID format '{}' in path.", request.params(":id"));
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid course ID format.")));
            return null;
        }
        auditLogger.info("User ID {} (Type: {}, Role: {}) requesting course by ID: {}",
                request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"), courseId);

        Optional<Course> courseOpt = courseService.getCourseById(courseId);
        if (courseOpt.isPresent()) {
            response.status(HttpStatus.OK_200);
            return courseOpt.get();
        } else {
            auditLogger.warn("Course ID {} not found for request by User ID {}.", courseId, request.attribute("userId"));
            response.status(HttpStatus.NOT_FOUND_404);
            return Collections.singletonMap("error", "Course not found");
        }
    }

    private Object updateCourse(Request request, Response response) {
        response.type("application/json");
        if (!isAdmin(request)) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to update course.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }
        // ... (остальная логика обновления курса как была, но с проверкой прав выше) ...
        Integer requestingUserId = request.attribute("userId"); // Для логирования
        auditLogger.info("Admin User ID {} attempting to update course. Path param ID: {}, Request body: {}", requestingUserId, request.params(":id"), request.body().substring(0, Math.min(request.body().length(), 200)));
        try {
            int id = Integer.parseInt(request.params(":id"));
            Course courseUpdates = JsonUtil.fromJson(request.body(), Course.class);

            if (courseUpdates.getCourseName() == null || courseUpdates.getCourseName().trim().isEmpty()) {
                auditLogger.warn("Course update failed (Bad Request - missing name) for ID {} by Admin User ID {}.", id, requestingUserId);
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Course name is required for update.")));
            }
            courseUpdates.setCourseId(id); // Убедимся, что ID из пути используется

            if (courseService.updateCourse(id, courseUpdates)) {
                auditLogger.info("Course ID {} updated successfully by Admin User ID {}.", id, requestingUserId);
                response.status(HttpStatus.OK_200);
                return courseService.getCourseById(id).orElse(null);
            } else {
                // ... (обработка ошибок как была) ...
                if (courseService.getCourseById(id).isPresent()) {
                    auditLogger.warn("Course ID {} update failed by Admin User ID {} (e.g. data unchanged or db error).", id, requestingUserId);
                    halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Course update failed, possibly due to invalid data or no changes.")));
                } else {
                    auditLogger.warn("Course ID {} not found for update by Admin User ID {}.", id, requestingUserId);
                    halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Course not found")));
                }
            }
        } catch (NumberFormatException e) {
            auditLogger.warn("Invalid course ID format '{}' for update by Admin User ID {}.", request.params(":id"), requestingUserId);
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid course ID format")));
        } catch (RuntimeException e) { // Ловим RuntimeException
            // ... (обработка ошибок JSON и других RuntimeException как была) ...
            if (e.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                logger.warn("Failed to parse update course request body for ID {} by User {}: {}", request.params(":id"), requestingUserId, e.getMessage());
                auditLogger.warn("Course update failed (bad JSON request). Path param ID: {}, User ID: {}. Error: {}", request.params(":id"), requestingUserId, e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Bad request: Invalid JSON format. " + e.getMessage())));
            } else if (e instanceof IllegalArgumentException) {
                logger.warn("Invalid data for updating course ID {} by User ID {}: {}", request.params(":id"), requestingUserId, e.getMessage());
                auditLogger.warn("Course update failed (Bad Request) by User ID {}: {}", request.params(":id"), requestingUserId, e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            } else {
                logger.error("Error updating course ID {} by User ID {}: {}", request.params(":id"), requestingUserId, e.getMessage(), e);
                auditLogger.error("Course update failed (Server Error) by User ID {}: {}",request.params(":id"), requestingUserId, e.getMessage());
                halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error during course update.")));
            }
        }
        return null;
    }

    private Object deleteCourse(Request request, Response response) {
        response.type("application/json");
        if (!isAdmin(request)) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to delete course.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }
        // ... (остальная логика удаления курса как была, но с проверкой прав выше) ...
        Integer requestingUserId = request.attribute("userId"); // Для логирования
        auditLogger.info("Admin User ID {} attempting to delete course. Path param ID: {}", requestingUserId, request.params(":id"));
        try {
            int id = Integer.parseInt(request.params(":id"));
            if (courseService.deleteCourse(id)) {
                auditLogger.info("Course ID {} deleted successfully by Admin User ID {}.", id, requestingUserId);
                response.status(HttpStatus.NO_CONTENT_204);
                return "";
            } else {
                // ... (обработка ошибок как была) ...
                if (courseService.getCourseById(id).isEmpty()) {
                    auditLogger.warn("Course ID {} not found for deletion request by Admin User ID {}.", id, requestingUserId);
                    response.status(HttpStatus.NOT_FOUND_404);
                    return Collections.singletonMap("error", "Course not found");
                } else {
                    auditLogger.error("Course ID {} delete failed for unknown reason for request by Admin User ID {}.", id, requestingUserId);
                    response.status(HttpStatus.INTERNAL_SERVER_ERROR_500);
                    return Collections.singletonMap("error", "Course delete failed");
                }
            }
        } catch (NumberFormatException e) {
            auditLogger.warn("Invalid course ID format '{}' for delete by Admin User ID {}.", request.params(":id"), requestingUserId);
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid course ID format")));
        } catch (Exception e) {
            logger.error("Error deleting course ID {} by Admin User ID {}: {}", request.params(":id"), requestingUserId, e.getMessage(), e);
            auditLogger.error("Course delete failed (Server Error) for ID {} by Admin User ID {}: {}", request.params(":id"), requestingUserId, e.getMessage());
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error during course deletion.")));
        }
        return null;
    }
}