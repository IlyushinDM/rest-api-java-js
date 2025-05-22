package org.example.education.dao;

import org.example.education.model.Grade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GradeDao {
    private static final Logger logger = LoggerFactory.getLogger(GradeDao.class);

    public Grade save(Grade grade) {
        String sql = "INSERT INTO grades (student_course_id, grade_value, grade_date, comments) VALUES (?, ?, ?, ?) RETURNING grade_id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, grade.getStudentCourseId());
            pstmt.setString(2, grade.getGradeValue());
            pstmt.setDate(3, grade.getGradeDate() != null ? Date.valueOf(grade.getGradeDate()) : Date.valueOf(LocalDate.now()));
            pstmt.setString(4, grade.getComments());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating grade failed, no rows affected.");
            }
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    grade.setGradeId(generatedKeys.getInt(1));
                    logger.info("Grade saved successfully for enrollment ID {}. Grade ID: {}", grade.getStudentCourseId(), grade.getGradeId());
                    return grade;
                } else {
                    throw new SQLException("Creating grade failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            // Проверка на foreign key constraint violation (если student_course_id не существует)
            if (e.getSQLState().startsWith("23")) { // Коды ошибок SQL, связанные с целостностью данных
                logger.warn("Failed to save grade due to data integrity violation (e.g., student_course_id not found): {}", e.getMessage());
                throw new RuntimeException("Cannot save grade: Invalid student enrollment reference.", e);
            }
            logger.error("Error saving grade for enrollment ID {}: {}", grade.getStudentCourseId(), e.getMessage(), e);
            throw new RuntimeException("Could not save grade: " + e.getMessage(), e);
        }
    }

    public Optional<Grade> findById(int gradeId) {
        String sql = "SELECT grade_id, student_course_id, grade_value, grade_date, comments FROM grades WHERE grade_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, gradeId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToGrade(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding grade by ID {}: {}", gradeId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Grade> findByStudentCourseId(int studentCourseId) {
        List<Grade> grades = new ArrayList<>();
        String sql = "SELECT grade_id, student_course_id, grade_value, grade_date, comments FROM grades WHERE student_course_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentCourseId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                grades.add(mapRowToGrade(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding grades for student_course_id {}: {}", studentCourseId, e.getMessage(), e);
        }
        return grades;
    }

    // Получение всех оценок для конкретного студента по всем его курсам
    public List<Grade> findByStudentId(int studentId) {
        List<Grade> grades = new ArrayList<>();
        String sql = "SELECT g.grade_id, g.student_course_id, g.grade_value, g.grade_date, g.comments " +
                "FROM grades g " +
                "JOIN student_courses sc ON g.student_course_id = sc.student_course_id " +
                "WHERE sc.student_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                grades.add(mapRowToGrade(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding grades for student ID {}: {}", studentId, e.getMessage(), e);
        }
        return grades;
    }


    public boolean update(Grade grade) {
        String sql = "UPDATE grades SET student_course_id = ?, grade_value = ?, grade_date = ?, comments = ? WHERE grade_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, grade.getStudentCourseId());
            pstmt.setString(2, grade.getGradeValue());
            pstmt.setDate(3, grade.getGradeDate() != null ? Date.valueOf(grade.getGradeDate()) : Date.valueOf(LocalDate.now()));
            pstmt.setString(4, grade.getComments());
            pstmt.setInt(5, grade.getGradeId());
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Grade ID {} updated successfully.", grade.getGradeId());
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            if (e.getSQLState().startsWith("23")) {
                logger.warn("Failed to update grade due to data integrity violation: {}", e.getMessage());
                throw new RuntimeException("Cannot update grade: Invalid student enrollment reference.", e);
            }
            logger.error("Error updating grade ID {}: {}", grade.getGradeId(), e.getMessage(), e);
            return false;
        }
    }

    public boolean delete(int gradeId) {
        String sql = "DELETE FROM grades WHERE grade_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, gradeId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Grade ID {} deleted successfully.", gradeId);
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.error("Error deleting grade ID {}: {}", gradeId, e.getMessage(), e);
            return false;
        }
    }

    private Grade mapRowToGrade(ResultSet rs) throws SQLException {
        Date gradeDateSql = rs.getDate("grade_date");
        LocalDate gradeDate = (gradeDateSql != null) ? gradeDateSql.toLocalDate() : null;
        return new Grade(
                rs.getInt("grade_id"),
                rs.getInt("student_course_id"),
                rs.getString("grade_value"),
                gradeDate,
                rs.getString("comments")
        );
    }
}