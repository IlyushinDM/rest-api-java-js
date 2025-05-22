package org.example.education.dao;

import org.example.education.model.Student;
import org.example.education.util.PasswordUtil; // Не используется здесь напрямую, но полезно помнить о нем для контекста
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentDao {
    private static final Logger logger = LoggerFactory.getLogger(StudentDao.class);

    public Optional<Student> findByEmail(String email) {
        String sql = "SELECT student_id, first_name, last_name, email, group_name FROM students WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToStudent(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding student by email: {}", email, e);
        }
        return Optional.empty();
    }

    public Optional<StudentWithPasswordHash> findByEmailForAuth(String email) {
        String sql = "SELECT student_id, first_name, last_name, email, group_name, password_hash FROM students WHERE email = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Student student = mapRowToStudent(rs);
                String passwordHash = rs.getString("password_hash");
                return Optional.of(new StudentWithPasswordHash(student, passwordHash));
            }
        } catch (SQLException e) {
            logger.error("Error finding student by email for auth: {}", email, e);
        }
        return Optional.empty();
    }

    public Optional<Student> findById(int id) {
        String sql = "SELECT student_id, first_name, last_name, email, group_name FROM students WHERE student_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToStudent(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding student by ID: {}", id, e);
        }
        return Optional.empty();
    }

    public List<Student> findAll() {
        List<Student> students = new ArrayList<>();
        String sql = "SELECT student_id, first_name, last_name, email, group_name FROM students ORDER BY last_name, first_name";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                students.add(mapRowToStudent(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all students", e);
        }
        return students;
    }

    public Student save(Student student, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            logger.error("Attempted to save student {} with an empty password.", student.getEmail());
            throw new IllegalArgumentException("Password cannot be empty for a new student.");
        }
        // Хеширование пароля происходит в StudentService перед вызовом этого DAO-метода,
        // или должно было бы происходить здесь, если бы rawPassword передавался для сохранения.
        // В текущей реализации StudentService.createStudent хеширует пароль и передает
        // уже хешированный пароль в StudentDao.save(student, HASHED_PASSWORD)
        // Давайте исправим это для консистентности: DAO должен принимать хешированный пароль.
        // Либо, если DAO принимает rawPassword, он должен его хешировать.
        // Для простоты, предположим, StudentService передает хешированный пароль, как и было.
        // Тогда параметр должен называться passwordHash.
        // Но в StudentService.createStudent используется PasswordUtil.hashPassword(rawPassword),
        // а затем studentDao.save(student, rawPassword) - это несоответствие.
        // ИСПРАВИМ: StudentDao.save принимает хешированный пароль.

        // Измененная сигнатура для ясности:
        // public Student save(Student student, String passwordHash)
        // Однако, в предыдущей версии мы передавали rawPassword и StudentService его хешировал.
        // Давайте вернемся к варианту, где DAO принимает хешированный пароль.
        // Это означает, что StudentService.createStudent должен выглядеть так:
        // String hashedPassword = PasswordUtil.hashPassword(rawPassword);
        // return studentDao.saveWithHashedPassword(student, hashedPassword);
        // Или StudentDao.save переименовать.
        // Для минимальных изменений оставим StudentDao.save(Student student, String passwordHash)
        // и StudentService.createStudent будет вызывать его с хешем.

        // Текущий save из StudentController вызывает studentService.createStudent(student, password);
        // studentService.createStudent(Student student, String rawPassword) { studentDao.save(student, rawPassword); }
        // А studentDao.save(student, rawPassword) внутри хеширует. Это нормально.
        // Сохраним текущую логику, где DAO хеширует при СОЗДАНИИ.

        String hashedPassword = PasswordUtil.hashPassword(rawPassword); // Хешируем здесь при создании

        String sql = "INSERT INTO students (first_name, last_name, email, group_name, password_hash) VALUES (?, ?, ?, ?, ?) RETURNING student_id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, student.getFirstName());
            pstmt.setString(2, student.getLastName());
            pstmt.setString(3, student.getEmail());
            pstmt.setString(4, student.getGroupName());
            pstmt.setString(5, hashedPassword); // Сохраняем хешированный пароль

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating student failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    student.setStudentId(generatedKeys.getInt(1));
                    return student;
                } else {
                    throw new SQLException("Creating student failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            // Проверка на уникальность email (код SQLState для PostgreSQL - 23505)
            if ("23505".equals(e.getSQLState())) {
                logger.warn("Error saving student: Email {} already exists.", student.getEmail());
                throw new RuntimeException("Email already exists.", e);
            }
            logger.error("Error saving student with email {}: {}", student.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Could not save student: " + e.getMessage(), e);
        }
    }

    /**
     * Обновляет данные студента. Если newPasswordHash предоставлен (не null и не пустой),
     * пароль также будет обновлен.
     * @param student объект Student с обновляемыми данными (кроме пароля).
     * @param newPasswordHash новый хешированный пароль или null, если пароль не меняется.
     * @return true, если обновление прошло успешно, иначе false.
     */
    public boolean update(Student student, String newPasswordHash) {
        // Формируем SQL-запрос динамически, чтобы включать обновление пароля только при необходимости
        StringBuilder sqlBuilder = new StringBuilder("UPDATE students SET first_name = ?, last_name = ?, email = ?, group_name = ?");

        // Используем List для хранения параметров, чтобы порядок соответствовал '?' в запросе
        List<Object> params = new ArrayList<>();
        params.add(student.getFirstName());
        params.add(student.getLastName());
        params.add(student.getEmail());
        params.add(student.getGroupName());

        boolean updatingPassword = (newPasswordHash != null && !newPasswordHash.trim().isEmpty());
        if (updatingPassword) {
            sqlBuilder.append(", password_hash = ?");
            params.add(newPasswordHash);
        }

        sqlBuilder.append(" WHERE student_id = ?");
        params.add(student.getStudentId());

        String sql = sqlBuilder.toString();
        logger.debug("Executing student update SQL: {}", sql);
        // Для безопасности, не логируем сами параметры, если там есть хеш пароля,
        // или логируем их выборочно, маскируя пароль.

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Устанавливаем параметры в PreparedStatement
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Student ID {} updated successfully. Password updated: {}", student.getStudentId(), updatingPassword);
            } else {
                logger.warn("Student ID {} not found or data was the same, no rows updated.", student.getStudentId());
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            // Проверка на уникальность email при обновлении
            if ("23505".equals(e.getSQLState())) {
                logger.warn("Error updating student ID {}: Email {} already exists for another student.", student.getStudentId(), student.getEmail());
                // Можно пробросить кастомное исключение или вернуть false с более специфичным логом
                throw new RuntimeException("Cannot update student: Email " + student.getEmail() + " is already in use.", e);
            }
            logger.error("Error updating student ID {}: {}", student.getStudentId(), e.getMessage(), e);
            return false;
        }
    }


    public boolean delete(int id) {
        String sql = "DELETE FROM students WHERE student_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Student ID {} deleted successfully.", id);
            } else {
                logger.warn("Student ID {} not found for deletion, no rows affected.", id);
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.error("Error deleting student ID {}: {}", id, e.getMessage(), e);
            return false;
        }
    }

    private Student mapRowToStudent(ResultSet rs) throws SQLException {
        return new Student(
                rs.getInt("student_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("group_name")
        );
    }

    // Вспомогательный класс для передачи студента вместе с хешем пароля внутри DAO/Service
    // Используется в findByEmailForAuth
    public static class StudentWithPasswordHash {
        public final Student student;
        public final String passwordHash;

        public StudentWithPasswordHash(Student student, String passwordHash) {
            this.student = student;
            this.passwordHash = passwordHash;
        }
    }
}