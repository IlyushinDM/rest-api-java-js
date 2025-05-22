package org.example.education.service;

import org.example.education.dao.EmployeeDao;
import org.example.education.dao.StudentDao;
import org.example.education.model.Session;
import org.example.education.model.UserCredentials;
import org.example.education.model.UserType;
import org.example.education.util.JwtUtil;
import org.example.education.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    private final StudentDao studentDao;
    private final EmployeeDao employeeDao;
    private final JwtUtil jwtUtil;

    public AuthService(StudentDao studentDao, EmployeeDao employeeDao, JwtUtil jwtUtil) {
        this.studentDao = studentDao;
        this.employeeDao = employeeDao;
        this.jwtUtil = jwtUtil;
    }

    public Optional<Session> login(UserCredentials credentials) {
        auditLogger.info("Login attempt for email: {}",
                (credentials != null && credentials.getEmail() != null) ? credentials.getEmail() : "UNKNOWN_EMAIL");

        if (credentials == null || credentials.getEmail() == null || credentials.getPassword() == null ||
                credentials.getEmail().trim().isEmpty() || credentials.getPassword().isEmpty()) {
            logger.warn("Login attempt with null or empty credentials for email: {}",
                    (credentials != null && credentials.getEmail() != null) ? credentials.getEmail() : "UNKNOWN_EMAIL");
            return Optional.empty();
        }

        String providedEmail = credentials.getEmail().trim();
        String providedPassword = credentials.getPassword();

        // 1. Ищем сотрудника (администратора/преподавателя)
        logger.debug("Attempting to authenticate as EMPLOYEE for email: {}", providedEmail);
        Optional<EmployeeDao.EmployeeWithPasswordHash> employeeOpt = employeeDao.findByEmailForAuth(providedEmail);

        if (employeeOpt.isPresent()) {
            EmployeeDao.EmployeeWithPasswordHash employeeData = employeeOpt.get();
            logger.debug("Employee found in DB: {}. Role: {}. Stored password hash (first 10): {}",
                    employeeData.employee.getEmail(),
                    employeeData.employee.getRole(),
                    employeeData.passwordHash.substring(0, Math.min(10, employeeData.passwordHash.length())) + "...");

            boolean passwordMatch = PasswordUtil.checkPassword(providedPassword, employeeData.passwordHash);
            logger.debug("Password check result for employee {}: {}", providedEmail, passwordMatch);

            if (passwordMatch) {
                String token = jwtUtil.generateToken(
                        employeeData.employee.getEmployeeId(),
                        employeeData.employee.getEmail(),
                        UserType.EMPLOYEE,
                        employeeData.employee.getRole()
                );
                long expiresAt = System.currentTimeMillis() + jwtUtil.getExpirationMillis();
                Session session = new Session(
                        token,
                        employeeData.employee.getEmployeeId(),
                        employeeData.employee.getEmail(),
                        UserType.EMPLOYEE,
                        employeeData.employee.getRole(),
                        expiresAt
                );
                auditLogger.info("SUCCESSFUL LOGIN: Employee {} (Role: {}) logged in.", providedEmail, employeeData.employee.getRole());
                logger.info("Employee {} ({}) logged in successfully.", providedEmail, employeeData.employee.getRole());
                return Optional.of(session);
            } else {
                // Сотрудник найден, но пароль неверный. Не продолжаем поиск студента.
                auditLogger.warn("FAILED LOGIN ATTEMPT: Invalid password for existing employee email: {}", providedEmail);
                logger.warn("Invalid password for employee email: {}", providedEmail);
                return Optional.empty();
            }
        }
        logger.debug("Employee not found for email: {}. Attempting to authenticate as STUDENT.", providedEmail);

        // 2. Ищем студента (только если сотрудник не был найден по этому email)
        Optional<StudentDao.StudentWithPasswordHash> studentOpt = studentDao.findByEmailForAuth(providedEmail);
        if (studentOpt.isPresent()) {
            StudentDao.StudentWithPasswordHash studentData = studentOpt.get();
            logger.debug("Student found in DB: {}. Stored password hash (first 10): {}",
                    studentData.student.getEmail(),
                    studentData.passwordHash.substring(0, Math.min(10, studentData.passwordHash.length())) + "...");

            boolean passwordMatch = PasswordUtil.checkPassword(providedPassword, studentData.passwordHash);
            logger.debug("Password check result for student {}: {}", providedEmail, passwordMatch);

            if (passwordMatch) {
                String token = jwtUtil.generateToken(
                        studentData.student.getStudentId(),
                        studentData.student.getEmail(),
                        UserType.STUDENT,
                        "STUDENT" // У студентов пока одна "роль"
                );
                long expiresAt = System.currentTimeMillis() + jwtUtil.getExpirationMillis();
                Session session = new Session(
                        token,
                        studentData.student.getStudentId(),
                        studentData.student.getEmail(),
                        UserType.STUDENT,
                        "STUDENT",
                        expiresAt
                );
                auditLogger.info("SUCCESSFUL LOGIN: Student {} logged in.", providedEmail);
                logger.info("Student {} logged in successfully.", providedEmail);
                return Optional.of(session);
            } else {
                auditLogger.warn("FAILED LOGIN ATTEMPT: Invalid password for existing student email: {}", providedEmail);
                logger.warn("Invalid password for student email: {}", providedEmail);
            }
        } else {
            // Пользователь не найден ни как сотрудник, ни как студент
            auditLogger.warn("FAILED LOGIN ATTEMPT: User not found with email: {}", providedEmail);
            logger.warn("User (neither employee nor student) not found with email: {}", providedEmail);
        }

        return Optional.empty(); // Если нигде не совпало или пароль неверный для найденного пользователя
    }

    public void logout(String token) {
        String tokenSnippet = (token != null && token.length() > 10) ? token.substring(0,10) + "..." : token;
        auditLogger.info("Logout request processed for token (first 10 chars): {}", tokenSnippet);
        logger.info("Logout request received. Client should clear token.");
        // Для JWT на стороне сервера обычно ничего делать не нужно, кроме возможного добавления токена в черный список.
    }
}