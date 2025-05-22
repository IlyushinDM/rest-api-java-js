package org.example.education.controller;

import org.example.education.model.Student;
import org.example.education.model.UserType;
import org.example.education.service.StudentService;
import org.example.education.util.JsonUtil;
// import com.fasterxml.jackson.core.JsonProcessingException; // Ловим RuntimeException
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static spark.Spark.*;

public class StudentController {
    private static final Logger logger = LoggerFactory.getLogger(StudentController.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
        setupRoutes();
    }

    private boolean isAdmin(Request request) {
        UserType userType = request.attribute("userType");
        String userRole = request.attribute("userRole");
        return userType == UserType.EMPLOYEE && "ADMIN".equals(userRole);
    }

    private void setupRoutes() {
        post("/api/students", this::createStudent, JsonUtil.jsonResponseTransformer());
        get("/api/students", this::getAllStudents, JsonUtil.jsonResponseTransformer());
        get("/api/students/:id", this::getStudentById, JsonUtil.jsonResponseTransformer());
        put("/api/students/:id", this::updateStudent, JsonUtil.jsonResponseTransformer());
        delete("/api/students/:id", this::deleteStudent, JsonUtil.jsonResponseTransformer());
    }

    private Object createStudent(Request request, Response response) {
        response.type("application/json");
        auditLogger.info("Attempt to create student (register). IP: {}. Body: {}", request.ip(), request.body().substring(0, Math.min(request.body().length(), 200)));
        try {
            Map<String, Object> payload = JsonUtil.fromJson(request.body(), Map.class);
            Student student = new Student();

            Object firstNameObj = payload.get("firstName");
            if (firstNameObj == null || ((String)firstNameObj).trim().isEmpty()) {
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "First name is required")));
            }
            student.setFirstName((String) firstNameObj);

            Object lastNameObj = payload.get("lastName");
            if (lastNameObj == null || ((String)lastNameObj).trim().isEmpty()) {
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Last name is required")));
            }
            student.setLastName((String) lastNameObj);

            Object emailObj = payload.get("email");
            if (emailObj == null || ((String)emailObj).trim().isEmpty()){
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Email is required")));
            }
            student.setEmail((String) emailObj);

            Object groupNameObj = payload.get("groupName");
            if (groupNameObj != null) {
                student.setGroupName((String) groupNameObj);
            }

            Object passwordObj = payload.get("password");
            if (passwordObj == null || ((String)passwordObj).trim().isEmpty()) {
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Password is required")));
            }
            String password = (String) passwordObj;

            Student createdStudent = studentService.createStudent(student, password);
            response.status(HttpStatus.CREATED_201);
            auditLogger.info("Student registered: ID {}, Email {}. Requested by IP: {}", createdStudent.getStudentId(), createdStudent.getEmail(), request.ip());
            return createdStudent;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                logger.warn("Failed to parse create student request body: {}", e.getMessage());
                auditLogger.warn("Student creation failed (bad JSON request). IP: {}. Error: {}", request.ip(), e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Bad request: Invalid JSON format. " + e.getMessage())));
            } else if (e instanceof IllegalArgumentException) {
                logger.warn("Invalid data for student creation. IP: {}. Error: {}", request.ip(), e.getMessage());
                auditLogger.warn("Student creation failed (Bad Request). IP: {}. Error: {}", request.ip(), e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            } else if (e.getMessage() != null && e.getMessage().toLowerCase().contains("email already exists")) {
                logger.warn("Student creation conflict (email exists). IP: {}. Error: {}", request.ip(), e.getMessage());
                auditLogger.warn("Student creation failed (Conflict - email exists). IP: {}. Error: {}", request.ip(), e.getMessage());
                halt(HttpStatus.CONFLICT_409, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            }
            else {
                logger.error("Error creating student: {}", e.getMessage(), e);
                auditLogger.error("Failed student creation (Server Error). IP: {}. Error: {}", request.ip(), e.getMessage());
                halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Could not create student: " + e.getMessage())));
            }
        }
        return null;
    }

    private Object getAllStudents(Request request, Response response) {
        response.type("application/json");
        if (!isAdmin(request)) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to get all students.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"));
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }
        auditLogger.info("Admin User ID {} requesting all students.", (Object) request.attribute("userId"));
        return studentService.getAllStudents();
    }

    private Object getStudentById(Request request, Response response) {
        response.type("application/json");
        int studentIdFromPath;
        try {
            studentIdFromPath = Integer.parseInt(request.params(":id"));
        } catch (NumberFormatException e) {
            auditLogger.warn("Invalid student ID format '{}' in path.", request.params(":id"));
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid student ID format.")));
            return null;
        }

        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        // String requestingUserRole = request.attribute("userRole"); // Не используется напрямую здесь

        auditLogger.info("User ID {} (Type: {}) requesting student by ID: {}",
                requestingUserId, requestingUserType, studentIdFromPath);

        if (!isAdmin(request) && (requestingUserType != UserType.STUDENT || studentIdFromPath != requestingUserId)) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}) to get profile of student ID {}.",
                    requestingUserId, requestingUserType, studentIdFromPath);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: Access denied.")));
        }

        Optional<Student> studentOpt = studentService.getStudentById(studentIdFromPath);
        if (studentOpt.isPresent()) {
            response.status(HttpStatus.OK_200);
            return studentOpt.get();
        } else {
            auditLogger.warn("Student ID {} not found for request by User ID {}.", studentIdFromPath, requestingUserId);
            response.status(HttpStatus.NOT_FOUND_404);
            return Collections.singletonMap("error", "Student not found");
        }
    }

    private Object updateStudent(Request request, Response response) {
        response.type("application/json");
        int studentIdToUpdate;
        try {
            studentIdToUpdate = Integer.parseInt(request.params(":id"));
        } catch (NumberFormatException e) {
            auditLogger.warn("Invalid student ID format '{}' for update.", request.params(":id"));
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid student ID format.")));
            return null;
        }

        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        // String requestingUserRole = request.attribute("userRole");

        auditLogger.info("User ID {} (Type: {}) attempting to update student ID {}. Body: {}",
                requestingUserId, requestingUserType, studentIdToUpdate, request.body().substring(0, Math.min(request.body().length(), 200)));

        // Разрешаем админу или самому студенту обновлять свой профиль
        boolean canUpdate = false;
        if (isAdmin(request)) {
            canUpdate = true;
        } else if (requestingUserType == UserType.STUDENT && studentIdToUpdate == requestingUserId) {
            canUpdate = true;
        }

        if (!canUpdate) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}) to update student ID {}.",
                    requestingUserId, requestingUserType, studentIdToUpdate);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: You do not have permission to update this student's data.")));
        }

        try {
            Student studentUpdates = JsonUtil.fromJson(request.body(), Student.class);
            String newPassword = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = JsonUtil.fromJson(request.body(), Map.class);
                if (payload.containsKey("newPassword") && payload.get("newPassword") != null) {
                    newPassword = ((String) payload.get("newPassword")).trim();
                    if (newPassword.isEmpty()) newPassword = null;
                }
            } catch (Exception parseEx) {
                logger.warn("Could not parse request body as map to check for newPassword while updating student ID {}. Error: {}", studentIdToUpdate, parseEx.getMessage());
            }

            // Если студент обновляет свой профиль, он не должен иметь возможность менять свой email или группу через этот эндпоинт,
            // если только это не специальная логика. Админ может менять все.
            if(requestingUserType == UserType.STUDENT) {
                Optional<Student> currentStudentDataOpt = studentService.getStudentById(studentIdToUpdate);
                if(currentStudentDataOpt.isPresent()){
                    Student currentStudentData = currentStudentDataOpt.get();
                    // Запрещаем студенту менять email и группу (пример)
                    if(!currentStudentData.getEmail().equals(studentUpdates.getEmail())){
                        halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Students cannot change their email address.")));
                    }
                    studentUpdates.setEmail(currentStudentData.getEmail()); // Восстанавливаем email

                    if(studentUpdates.getGroupName() != null && !studentUpdates.getGroupName().equals(currentStudentData.getGroupName())) {
                        halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Students cannot change their group directly.")));
                    }
                    studentUpdates.setGroupName(currentStudentData.getGroupName()); // Восстанавливаем группу
                } else {
                    halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Student not found."))); // Не должно случиться, если canUpdate=true
                }
            }


            if (studentService.updateStudent(studentIdToUpdate, studentUpdates, newPassword)) {
                auditLogger.info("Student ID {} updated successfully by User ID {}. Password changed: {}", studentIdToUpdate, requestingUserId, (newPassword != null));
                response.status(HttpStatus.OK_200);
                return studentService.getStudentById(studentIdToUpdate).orElse(null);
            } else {
                if (studentService.getStudentById(studentIdToUpdate).isPresent()) {
                    auditLogger.warn("Student ID {} update failed by User ID {} (e.g., data unchanged, validation error, or db error).", studentIdToUpdate, requestingUserId);
                    halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Student update failed. Ensure data is valid or different.")));
                } else {
                    auditLogger.warn("Student ID {} not found for update by User ID {}.", studentIdToUpdate, requestingUserId);
                    halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Student not found")));
                }
            }
        } catch (RuntimeException e) {
            if (e.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Bad request: Invalid JSON format. " + e.getMessage())));
            } else if (e instanceof IllegalArgumentException) {
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            } else if (e.getMessage() != null && e.getMessage().toLowerCase().contains("email already exists")) {
                halt(HttpStatus.CONFLICT_409, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            }
            else {
                logger.error("Error updating student ID {} by User {}", studentIdToUpdate, requestingUserId, e);
                auditLogger.error("Student update failed (Server Error). Path param ID: {}, User ID: {}. Error: {}", studentIdToUpdate, requestingUserId, e.getMessage());
                halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error during student update.")));
            }
        }
        return null;
    }

    private Object deleteStudent(Request request, Response response) {
        response.type("application/json");
        int studentIdToDelete;
        try {
            studentIdToDelete = Integer.parseInt(request.params(":id"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid student ID format.")));
            return null;
        }

        if (!isAdmin(request)) {
            auditLogger.warn("Forbidden attempt by User ID {} (Type: {}, Role: {}) to delete student ID {}.",
                    request.attribute("userId"), request.attribute("userType"), request.attribute("userRole"), studentIdToDelete);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: You do not have permission to delete students.")));
        }

        auditLogger.info("Admin User ID {} attempting to delete student ID {}", (Object) request.attribute("userId"), studentIdToDelete);

        try {
            if (studentService.deleteStudent(studentIdToDelete)) {
                auditLogger.info("Student ID {} deleted successfully by Admin User ID {}.", studentIdToDelete, request.attribute("userId"));
                response.status(HttpStatus.NO_CONTENT_204);
                return "";
            } else {
                auditLogger.warn("Student ID {} not found or delete failed for request by Admin User ID {}.", studentIdToDelete, request.attribute("userId"));
                response.status(HttpStatus.NOT_FOUND_404);
                return Collections.singletonMap("error", "Student not found or delete failed");
            }
        } catch (Exception e) {
            logger.error("Error deleting student ID {} by Admin User ID {}", studentIdToDelete, request.attribute("userId"), e);
            auditLogger.error("Student delete failed (Server Error). Student ID: {}, Admin User ID: {}. Error: {}", studentIdToDelete, request.attribute("userId"), e.getMessage());
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error during student deletion.")));
        }
        return null;
    }
}