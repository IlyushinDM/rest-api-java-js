package org.example.education.dao;

import org.example.education.model.StudentCourse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EnrollmentDao {
    private static final Logger logger = LoggerFactory.getLogger(EnrollmentDao.class);

    public StudentCourse enrollStudent(int studentId, int courseId, LocalDate enrollmentDate) {
        String sql = "INSERT INTO student_courses (student_id, course_id, enrollment_date) VALUES (?, ?, ?) RETURNING student_course_id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, studentId);
            pstmt.setInt(2, courseId);
            pstmt.setDate(3, Date.valueOf(enrollmentDate));

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Enrolling student failed, no rows affected.");
            }
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int studentCourseId = generatedKeys.getInt(1);
                    logger.info("Student {} enrolled in course {} successfully. Enrollment ID: {}", studentId, courseId, studentCourseId);
                    return new StudentCourse(studentCourseId, studentId, courseId, enrollmentDate);
                } else {
                    throw new SQLException("Enrolling student failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            if (e.getSQLState().equals("23505")) { // Unique violation
                logger.warn("Student {} is already enrolled in course {}.", studentId, courseId);
                throw new RuntimeException("Student is already enrolled in this course.", e);
            }
            logger.error("Error enrolling student {} in course {}: {}", studentId, courseId, e.getMessage(), e);
            throw new RuntimeException("Could not enroll student: " + e.getMessage(), e);
        }
    }

    public boolean unenrollStudent(int studentCourseId) {
        // Перед удалением записи из student_courses, убедитесь, что оценки (grades) для этой записи удаляются
        // или обрабатываются. Схема БД с ON DELETE CASCADE для grades(student_course_id) позаботится об этом.
        String sql = "DELETE FROM student_courses WHERE student_course_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentCourseId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Enrollment ID {} unenrolled successfully.", studentCourseId);
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.error("Error unenrolling student for enrollment ID {}: {}", studentCourseId, e.getMessage(), e);
            return false;
        }
    }

    public Optional<StudentCourse> findById(int studentCourseId) {
        String sql = "SELECT student_course_id, student_id, course_id, enrollment_date FROM student_courses WHERE student_course_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentCourseId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToStudentCourse(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding enrollment by ID {}: {}", studentCourseId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<StudentCourse> findByStudentId(int studentId) {
        List<StudentCourse> enrollments = new ArrayList<>();
        String sql = "SELECT sc.student_course_id, sc.student_id, sc.course_id, sc.enrollment_date " +
                "FROM student_courses sc " +
                "WHERE sc.student_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                enrollments.add(mapRowToStudentCourse(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding enrollments for student ID {}: {}", studentId, e.getMessage(), e);
        }
        return enrollments;
    }

    public List<StudentCourse> findByCourseId(int courseId) {
        List<StudentCourse> enrollments = new ArrayList<>();
        String sql = "SELECT sc.student_course_id, sc.student_id, sc.course_id, sc.enrollment_date " +
                "FROM student_courses sc " +
                "WHERE sc.course_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                enrollments.add(mapRowToStudentCourse(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding enrollments for course ID {}: {}", courseId, e.getMessage(), e);
        }
        return enrollments;
    }

    public List<StudentCourse> findAll() { // Новый метод
        List<StudentCourse> enrollments = new ArrayList<>();
        String sql = "SELECT student_course_id, student_id, course_id, enrollment_date FROM student_courses ORDER BY student_course_id";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                enrollments.add(mapRowToStudentCourse(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all enrollments: {}", e.getMessage(), e);
        }
        return enrollments;
    }

    private StudentCourse mapRowToStudentCourse(ResultSet rs) throws SQLException {
        return new StudentCourse(
                rs.getInt("student_course_id"),
                rs.getInt("student_id"),
                rs.getInt("course_id"),
                rs.getDate("enrollment_date").toLocalDate()
        );
    }
}