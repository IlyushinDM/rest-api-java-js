package org.example.education.controller;

import org.example.education.model.StudentCourse;
import org.example.education.model.UserType;
import org.example.education.service.EnrollmentService;
import org.example.education.util.JsonUtil;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Collections;
import java.util.List; // Добавлен импорт
import java.util.Map;
import java.util.Optional;

import static spark.Spark.*;

public class EnrollmentController {
    private static final Logger logger = LoggerFactory.getLogger(EnrollmentController.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
        setupRoutes();
    }

    private boolean isAdmin(Request request) {
        UserType userType = request.attribute("userType");
        String userRole = request.attribute("userRole");
        return userType == UserType.EMPLOYEE && "ADMIN".equals(userRole);
    }

    private void setupRoutes() {
        post("/api/enrollments", this::enrollStudent, JsonUtil.jsonResponseTransformer());
        delete("/api/enrollments/:enrollmentId", this::unenrollStudent, JsonUtil.jsonResponseTransformer());
        get("/api/enrollments/:enrollmentId", this::getEnrollmentById, JsonUtil.jsonResponseTransformer());
        get("/api/students/:studentId/enrollments", this::getEnrollmentsForStudent, JsonUtil.jsonResponseTransformer());
        get("/api/courses/:courseId/enrollments", this::getEnrollmentsForCourse, JsonUtil.jsonResponseTransformer());
        get("/api/enrollments", this::getAllEnrollmentsAdmin, JsonUtil.jsonResponseTransformer()); // Новый маршрут
    }

    private Object enrollStudent(Request request, Response response) {
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");

        auditLogger.info("User ID {} (Type: {}) attempting to create enrollment. Body: {}",
                requestingUserId, requestingUserType, request.body().substring(0, Math.min(request.body().length(), 200)));

        try {
            @SuppressWarnings("unchecked") // Безопасно, если JSON структура известна
            Map<String, Object> payload = JsonUtil.fromJson(request.body(), Map.class);
            Object studentIdObj = payload.get("studentId");
            Object courseIdObj = payload.get("courseId");

            if (studentIdObj == null || courseIdObj == null) {
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "studentId and courseId are required.")));
            }

            Integer studentIdToEnroll = (studentIdObj instanceof Number) ? ((Number) studentIdObj).intValue() : Integer.parseInt(studentIdObj.toString());
            Integer courseId = (courseIdObj instanceof Number) ? ((Number) courseIdObj).intValue() : Integer.parseInt(courseIdObj.toString());


            boolean canEnroll = false;
            if (requestingUserType == UserType.STUDENT && studentIdToEnroll.equals(requestingUserId)) {
                canEnroll = true;
            } else if (isAdmin(request)) {
                canEnroll = true;
            }

            if (!canEnroll) {
                auditLogger.warn("Forbidden enrollment attempt by User ID {} for studentId {}.", requestingUserId, studentIdToEnroll);
                halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: You do not have permission to perform this enrollment.")));
            }

            StudentCourse enrollment = enrollmentService.enrollStudent(studentIdToEnroll, courseId);
            response.status(HttpStatus.CREATED_201);
            auditLogger.info("Student {} enrolled in course {} by User ID {}. Enrollment ID: {}", studentIdToEnroll, courseId, requestingUserId, enrollment.getStudentCourseId());
            return enrollment;
        } catch (NumberFormatException | ClassCastException cce){ // Объединяем ошибки парсинга
            logger.warn("Error parsing enrollment payload (studentId/courseId not integers) by User ID {}: {}", requestingUserId, cce.getMessage());
            auditLogger.warn("Enrollment failed (Bad Request - invalid types in payload) by User ID {}: {}", requestingUserId, cce.getMessage());
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "studentId and courseId must be valid numbers.")));
        } catch (IllegalArgumentException e) {
            handleEnrollmentException(e, requestingUserId, response, "Enrollment failed (Bad Request/Not Found)");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("already enrolled")) {
                handleEnrollmentException(e, requestingUserId, response, "Enrollment failed (Conflict)", HttpStatus.CONFLICT_409);
            } else {
                handleEnrollmentException(e, requestingUserId, response, "Enrollment failed (Server Error)", HttpStatus.INTERNAL_SERVER_ERROR_500);
            }
        }
        return null;
    }

    private void handleEnrollmentException(Exception e, Integer userId, Response response, String logSummary, int statusCode) {
        logger.warn("{} by User ID {}: {}", logSummary, userId, e.getMessage(), e);
        auditLogger.warn("{} by User ID {}: {}", logSummary, userId, e.getMessage());
        response.status(statusCode); // Устанавливаем статус перед halt
        halt(statusCode, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
    }

    private void handleEnrollmentException(Exception e, Integer userId, Response response, String logSummary) {
        int statusCode = HttpStatus.BAD_REQUEST_400; // По умолчанию
        if (e.getMessage() != null && (e.getMessage().contains("Student not found") || e.getMessage().contains("Course not found"))) {
            statusCode = HttpStatus.NOT_FOUND_404;
        }
        handleEnrollmentException(e, userId, response, logSummary, statusCode);
    }


    private Object unenrollStudent(Request request, Response response) {
        // ... (логика с проверкой прав как была) ...
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int enrollmentId;
        try {
            enrollmentId = Integer.parseInt(request.params(":enrollmentId"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid enrollment ID format.")));
            return null;
        }
        auditLogger.info("User ID {} (Type: {}) attempting to unenroll by enrollment ID: {}",
                requestingUserId, requestingUserType, enrollmentId);

        Optional<StudentCourse> enrollmentOpt = enrollmentService.getEnrollmentById(enrollmentId);
        if (enrollmentOpt.isEmpty()) {
            halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Enrollment not found.")));
        }

        boolean canUnenroll = false;
        if (requestingUserType == UserType.STUDENT && enrollmentOpt.get().getStudentId() == requestingUserId) {
            canUnenroll = true;
        } else if (isAdmin(request)) {
            canUnenroll = true;
        }

        if (!canUnenroll) {
            auditLogger.warn("Forbidden unenrollment attempt by User ID {} for enrollment ID {}.", requestingUserId, enrollmentId);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: You do not have permission to perform this action.")));
        }

        try {
            if (enrollmentService.unenrollStudent(enrollmentId)) {
                auditLogger.info("Enrollment ID {} unenrolled successfully by User ID {}.", enrollmentId, requestingUserId);
                response.status(HttpStatus.NO_CONTENT_204);
                return "";
            } else {
                auditLogger.warn("Enrollment ID {} unenrollment failed for request by User ID {}.", enrollmentId, requestingUserId);
                halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Unenrollment failed unexpectedly.")));
            }
        } catch (Exception e) {
            logger.error("Error unenrolling (enrollment ID {}) by User ID {}: {}", enrollmentId, requestingUserId, e.getMessage(), e);
            auditLogger.error("Unenrollment failed (Server Error) for enrollment ID {} by User ID {}: {}", enrollmentId, requestingUserId, e.getMessage());
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error during unenrollment.")));
        }
        return null;
    }

    private Object getEnrollmentById(Request request, Response response) {
        // ... (логика с проверкой прав как была) ...
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int enrollmentId;
        try {
            enrollmentId = Integer.parseInt(request.params(":enrollmentId"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid enrollment ID format.")));
            return null;
        }
        auditLogger.info("User ID {} (Type: {}) requesting enrollment by ID: {}", requestingUserId, requestingUserType, enrollmentId);

        Optional<StudentCourse> enrollmentOpt = enrollmentService.getEnrollmentById(enrollmentId);
        if (enrollmentOpt.isEmpty()) {
            halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Enrollment not found.")));
        }

        StudentCourse enrollment = enrollmentOpt.get();
        boolean canView = false;
        if (requestingUserType == UserType.STUDENT && enrollment.getStudentId() == requestingUserId) {
            canView = true;
        } else if (isAdmin(request)) {
            canView = true;
        }

        if (!canView) {
            auditLogger.warn("Forbidden attempt by User ID {} to view enrollment ID {}.", requestingUserId, enrollmentId);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden to access this enrollment.")));
        }
        response.status(HttpStatus.OK_200);
        return enrollment;
    }

    private Object getEnrollmentsForStudent(Request request, Response response) {
        // ... (логика с проверкой прав как была) ...
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int studentIdFromPath;
        try {
            studentIdFromPath = Integer.parseInt(request.params(":studentId"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid student ID format.")));
            return null;
        }
        auditLogger.info("User ID {} (Type: {}) requesting enrollments for student ID: {}", requestingUserId, requestingUserType, studentIdFromPath);

        boolean canView = false;
        if (requestingUserType == UserType.STUDENT && studentIdFromPath == requestingUserId) {
            canView = true;
        } else if (isAdmin(request)) {
            canView = true;
        }

        if (!canView) {
            auditLogger.warn("Forbidden attempt by User ID {} to get enrollments for student ID {}.", requestingUserId, studentIdFromPath);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: You can only view your own enrollments.")));
        }
        response.status(HttpStatus.OK_200);
        return enrollmentService.getEnrollmentsForStudent(studentIdFromPath);
    }

    private Object getEnrollmentsForCourse(Request request, Response response) {
        // ... (логика с проверкой прав как была) ...
        response.type("application/json");
        if (!isAdmin(request)) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to get enrollments for course.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }

        int courseId;
        try {
            courseId = Integer.parseInt(request.params(":courseId"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid course ID format.")));
            return null;
        }
        auditLogger.info("Admin User ID {} requesting enrollments for course ID: {}", (Object)request.attribute("userId"), courseId);
        response.status(HttpStatus.OK_200);
        return enrollmentService.getEnrollmentsForCourse(courseId);
    }

    private Object getAllEnrollmentsAdmin(Request request, Response response) {
        response.type("application/json");
        if (!isAdmin(request)) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to get all enrollments.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }

        Integer adminUserId = request.attribute("userId");
        auditLogger.info("Admin User ID {} requesting all enrollments.", adminUserId);

        List<StudentCourse> enrollments = enrollmentService.getAllEnrollments();
        response.status(HttpStatus.OK_200);
        return enrollments;
    }
}