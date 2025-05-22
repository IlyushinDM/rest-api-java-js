package org.example.education.dao;

import org.example.education.model.Employee;
import org.example.education.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;
// Пока не будем реализовывать полный CRUD для сотрудников через API, только поиск для логина
// и, возможно, создание через консоль или напрямую в БД для админов.

public class EmployeeDao {
    private static final Logger logger = LoggerFactory.getLogger(EmployeeDao.class);

    // Вспомогательный класс для передачи сотрудника вместе с хешем пароля
    public static class EmployeeWithPasswordHash {
        public final Employee employee;
        public final String passwordHash;

        public EmployeeWithPasswordHash(Employee employee, String passwordHash) {
            this.employee = employee;
            this.passwordHash = passwordHash;
        }
    }

    public Optional<EmployeeWithPasswordHash> findByEmailForAuth(String email) {
        String sql = "SELECT employee_id, first_name, last_name, email, password_hash, role FROM employees WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Employee employee = new Employee(
                        rs.getInt("employee_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("role")
                );
                String passwordHash = rs.getString("password_hash");
                return Optional.of(new EmployeeWithPasswordHash(employee, passwordHash));
            }
        } catch (SQLException e) {
            logger.error("Error finding employee by email for auth: {}", email, e);
        }
        return Optional.empty();
    }

    // Метод для создания сотрудника (может понадобиться для админ-панели или тестов)
    public Employee save(Employee employee, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty for a new employee.");
        }
        String hashedPassword = PasswordUtil.hashPassword(rawPassword);

        String sql = "INSERT INTO employees (first_name, last_name, email, password_hash, role) VALUES (?, ?, ?, ?, ?) RETURNING employee_id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, employee.getFirstName());
            pstmt.setString(2, employee.getLastName());
            pstmt.setString(3, employee.getEmail());
            pstmt.setString(4, hashedPassword);
            pstmt.setString(5, employee.getRole() != null ? employee.getRole() : "ADMIN"); // Роль по умолчанию

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating employee failed, no rows affected.");
            }
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    employee.setEmployeeId(generatedKeys.getInt(1));
                    logger.info("Employee saved successfully with ID: {}", employee.getEmployeeId());
                    return employee;
                } else {
                    throw new SQLException("Creating employee failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving employee with email {}: {}", employee.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Could not save employee: " + e.getMessage(), e);
        }
    }
}