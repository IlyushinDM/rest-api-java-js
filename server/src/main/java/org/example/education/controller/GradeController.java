package org.example.education.controller;

import org.example.education.model.Grade;
import org.example.education.model.StudentCourse;
import org.example.education.model.UserType; // Импорт
import org.example.education.service.EnrollmentService;
import org.example.education.service.GradeService;
import org.example.education.util.JsonUtil;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static spark.Spark.*;

public class GradeController {
    private static final Logger logger = LoggerFactory.getLogger(GradeController.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private final GradeService gradeService;
    private final EnrollmentService enrollmentService;

    public GradeController(GradeService gradeService, EnrollmentService enrollmentService) {
        this.gradeService = gradeService;
        this.enrollmentService = enrollmentService;
        setupRoutes();
    }

    private boolean isAdmin(Request request) {
        UserType userType = request.attribute("userType");
        String userRole = request.attribute("userRole");
        return userType == UserType.EMPLOYEE && "ADMIN".equals(userRole);
    }
    // TODO: Добавить isTeacher(request), если будет такая роль и логика проверки

    private void setupRoutes() {
        // Только админ (или преподаватель) может добавлять, обновлять, удалять оценки
        post("/api/grades", this::addGrade, JsonUtil.jsonResponseTransformer());
        put("/api/grades/:gradeId", this::updateGrade, JsonUtil.jsonResponseTransformer());
        delete("/api/grades/:gradeId", this::deleteGrade, JsonUtil.jsonResponseTransformer());

        // Просмотр оценок
        get("/api/grades/:gradeId", this::getGradeById, JsonUtil.jsonResponseTransformer());
        get("/api/enrollments/:enrollmentId/grades", this::getGradesForEnrollment, JsonUtil.jsonResponseTransformer());
        get("/api/students/:studentId/grades", this::getGradesForStudent, JsonUtil.jsonResponseTransformer());
    }

    private Object addGrade(Request request, Response response) {
        response.type("application/json");
        if (!isAdmin(request) /* && !isTeacher(request) */) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to add grade.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }

        Integer requestingUserId = request.attribute("userId"); // Для логирования
        auditLogger.info("Admin/Teacher User ID {} attempting to add grade. Body: {}", requestingUserId, request.body());
        try {
            Grade grade = JsonUtil.fromJson(request.body(), Grade.class);
            // Дополнительная проверка, что преподаватель ставит оценку своему студенту на своем курсе
            Grade addedGrade = gradeService.addGrade(grade);
            response.status(HttpStatus.CREATED_201);
            auditLogger.info("Grade added by User ID {} for enrollment ID {}. Grade ID: {}", requestingUserId, grade.getStudentCourseId(), addedGrade.getGradeId());
            return addedGrade;
        } catch (IllegalArgumentException e) { // Отдельный catch
            if (e.getMessage() != null && e.getMessage().contains("Invalid student enrollment reference")) {
                auditLogger.warn("Grade addition failed (Not Found - enrollment ref) by User ID {}: {}", requestingUserId, e.getMessage());
                halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            } else {
                logger.warn("Invalid data for adding grade by User ID {}: {}", requestingUserId, e.getMessage());
                auditLogger.warn("Grade addition failed (Bad Request) by User ID {}: {}", requestingUserId, e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            }
        } catch (RuntimeException e) { // Общий catch
            logger.error("Error adding grade by User ID {}: {}", requestingUserId, e.getMessage(), e);
            auditLogger.error("Grade addition failed (Server Error) by User ID {}: {}", requestingUserId, e.getMessage());
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Could not add grade: " + e.getMessage())));
        }
        return null;
    }

    private Object getGradeById(Request request, Response response) {
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int gradeId;
        try {
            gradeId = Integer.parseInt(request.params(":gradeId"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid grade ID format.")));
            return null;
        }
        auditLogger.info("User ID {} (Type: {}) requesting grade by ID: {}", requestingUserId, requestingUserType, gradeId);

        Optional<Grade> gradeOpt = gradeService.getGradeById(gradeId);
        if (gradeOpt.isEmpty()) {
            halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Grade not found.")));
        }

        Grade grade = gradeOpt.get();
        Optional<StudentCourse> enrollmentOpt = enrollmentService.getEnrollmentById(grade.getStudentCourseId());
        if(enrollmentOpt.isEmpty()){ // Маловероятно, если оценка существует
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Associated enrollment not found for grade.")));
        }

        boolean canView = false;
        if (isAdmin(request) /* || isTeacherForThisEnrollment(request, enrollmentOpt.get()) */) {
            canView = true;
        } else if (requestingUserType == UserType.STUDENT && enrollmentOpt.get().getStudentId() == requestingUserId) {
            canView = true;
        }

        if (!canView) {
            auditLogger.warn("Forbidden attempt by User ID {} to access grade ID {}.", requestingUserId, gradeId);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden to access this grade.")));
        }
        return grade;
    }

    private Object getGradesForEnrollment(Request request, Response response) {
        // Логика аналогична getGradeById - проверяем, кому принадлежит enrollmentId
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
        auditLogger.info("User ID {} (Type: {}) requesting grades for enrollment ID: {}", requestingUserId, requestingUserType, enrollmentId);

        Optional<StudentCourse> enrollmentOpt = enrollmentService.getEnrollmentById(enrollmentId);
        if (enrollmentOpt.isEmpty()) {
            halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Enrollment record not found.")));
        }

        boolean canView = false;
        if (isAdmin(request) /* || isTeacherForThisEnrollment(request, enrollmentOpt.get()) */) {
            canView = true;
        } else if (requestingUserType == UserType.STUDENT && enrollmentOpt.get().getStudentId() == requestingUserId) {
            canView = true;
        }

        if (!canView) {
            auditLogger.warn("Forbidden attempt by User ID {} to access grades for enrollment ID {}.", requestingUserId, enrollmentId);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden to access grades for this enrollment.")));
        }
        return gradeService.getGradesForEnrollment(enrollmentId);
    }

    private Object getGradesForStudent(Request request, Response response) {
        // Логика аналогична StudentController.getStudentById - проверяем, чьи оценки запрашиваются
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
        auditLogger.info("User ID {} (Type: {}) requesting all grades for student ID: {}", requestingUserId, requestingUserType, studentIdFromPath);

        boolean canView = false;
        if (isAdmin(request) /* || isTeacherOfThisStudent(request, studentIdFromPath) */) {
            canView = true;
        } else if (requestingUserType == UserType.STUDENT && studentIdFromPath == requestingUserId) {
            canView = true;
        }

        if (!canView) {
            auditLogger.warn("Forbidden attempt by User ID {} to access grades for student ID {}.", requestingUserId, studentIdFromPath);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: You can only view your own grades.")));
        }
        return gradeService.getGradesForStudent(studentIdFromPath);
    }

    private Object updateGrade(Request request, Response response) {
        response.type("application/json");
        if (!isAdmin(request) /* && !isTeacher(request) */) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to update grade.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }
        // ... (остальная логика как была, с проверкой прав выше) ...
        Integer requestingUserId = request.attribute("userId"); // Для логирования
        try {
            int gradeId = Integer.parseInt(request.params(":gradeId"));
            auditLogger.info("Admin/Teacher User ID {} attempting to update grade ID {}. Body: {}", requestingUserId, gradeId, request.body());

            Grade gradeData = JsonUtil.fromJson(request.body(), Grade.class);

            Optional<Grade> existingGradeOpt = gradeService.getGradeById(gradeId);
            if (existingGradeOpt.isEmpty()) {
                auditLogger.warn("Grade ID {} not found for update by User ID {}.", gradeId, requestingUserId);
                halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Grade not found.")));
            }
            // Доп. проверка, что преподаватель обновляет оценку своему студенту

            if (gradeService.updateGrade(gradeId, gradeData)) {
                auditLogger.info("Grade ID {} updated successfully by User ID {}.", gradeId, requestingUserId);
                response.status(HttpStatus.OK_200);
                return gradeService.getGradeById(gradeId).orElse(null);
            } else {
                auditLogger.warn("Grade ID {} update failed by User ID {} (e.g. not found or db error).", gradeId, requestingUserId);
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Grade update failed.")));
            }
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid grade ID format.")));
        } catch (IllegalArgumentException e) { // Отдельный catch
            if (e.getMessage() != null && e.getMessage().contains("Invalid student enrollment reference")) {
                auditLogger.warn("Grade update failed (Not Found - enrollment ref) by User ID {}: {}", request.params(":gradeId"), requestingUserId, e.getMessage());
                halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            } else {
                logger.warn("Invalid data for updating grade ID {} by User ID {}: {}",request.params(":gradeId"), requestingUserId, e.getMessage());
                auditLogger.warn("Grade update failed (Bad Request) by User ID {}: {}",request.params(":gradeId"), requestingUserId, e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            }
        } catch (RuntimeException e) { // Общий catch
            logger.error("Error updating grade ID {} by User ID {}: {}", request.params(":gradeId"), requestingUserId, e.getMessage(), e);
            auditLogger.error("Grade update failed (Server Error) for ID {} by User ID {}: {}",request.params(":gradeId"), requestingUserId, e.getMessage());
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error during grade update.")));
        }
        return null;
    }

    private Object deleteGrade(Request request, Response response) {
        response.type("application/json");
        if (!isAdmin(request) /* && !isTeacher(request) */) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to delete grade.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }
        // ... (остальная логика как была, с проверкой прав выше) ...
        Integer requestingUserId = request.attribute("userId"); // Для логирования
        try {
            int gradeId = Integer.parseInt(request.params(":gradeId"));
            auditLogger.info("Admin/Teacher User ID {} attempting to delete grade ID: {}", requestingUserId, gradeId);
            // Доп. проверка, что преподаватель удаляет оценку своему студенту

            if (gradeService.deleteGrade(gradeId)) {
                auditLogger.info("Grade ID {} deleted successfully by User ID {}.", gradeId, requestingUserId);
                response.status(HttpStatus.NO_CONTENT_204);
                return "";
            } else {
                auditLogger.warn("Grade ID {} not found or delete failed for request by User ID {}.", gradeId, requestingUserId);
                response.status(HttpStatus.NOT_FOUND_404);
                return Collections.singletonMap("error", "Grade not found or delete failed.");
            }
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid grade ID format.")));
        } catch (Exception e) {
            logger.error("Error deleting grade ID {} by User ID {}: {}", request.params(":gradeId"), requestingUserId, e.getMessage(), e);
            auditLogger.error("Grade delete failed (Server Error) for ID {} by User ID {}: {}", request.params(":gradeId"), requestingUserId, e.getMessage());
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error during grade deletion.")));
        }
        return null;
    }
}