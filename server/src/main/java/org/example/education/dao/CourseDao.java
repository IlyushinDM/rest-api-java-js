package org.example.education.dao;

import org.example.education.model.Course;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CourseDao {
    private static final Logger logger = LoggerFactory.getLogger(CourseDao.class);

    public Course save(Course course) {
        String sql = "INSERT INTO courses (course_name, description) VALUES (?, ?) RETURNING course_id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, course.getCourseName());
            pstmt.setString(2, course.getDescription());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating course failed, no rows affected.");
            }
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    course.setCourseId(generatedKeys.getInt(1));
                    logger.info("Course saved successfully: {}", course.getCourseName());
                    return course;
                } else {
                    throw new SQLException("Creating course failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving course {}: {}", course.getCourseName(), e.getMessage(), e);
            throw new RuntimeException("Could not save course: " + e.getMessage(), e);
        }
    }

    public Optional<Course> findById(int courseId) {
        String sql = "SELECT course_id, course_name, description FROM courses WHERE course_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToCourse(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding course by ID {}: {}", courseId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Course> findAll() {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT course_id, course_name, description FROM courses";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                courses.add(mapRowToCourse(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all courses: {}", e.getMessage(), e);
        }
        return courses;
    }

    public boolean update(Course course) {
        String sql = "UPDATE courses SET course_name = ?, description = ? WHERE course_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, course.getCourseName());
            pstmt.setString(2, course.getDescription());
            pstmt.setInt(3, course.getCourseId());
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Course updated successfully: {}", course.getCourseName());
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.error("Error updating course {}: {}", course.getCourseName(), e.getMessage(), e);
            return false;
        }
    }

    public boolean delete(int courseId) {
        // Дополнительно можно проверить, есть ли студенты на курсе, и запретить удаление
        // или каскадно удалить записи из student_courses. Текущая схема БД с ON DELETE CASCADE
        // для student_courses позаботится об этом.
        String sql = "DELETE FROM courses WHERE course_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, courseId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Course deleted successfully: ID {}", courseId);
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.error("Error deleting course ID {}: {}", courseId, e.getMessage(), e);
            return false;
        }
    }

    private Course mapRowToCourse(ResultSet rs) throws SQLException {
        return new Course(
                rs.getInt("course_id"),
                rs.getString("course_name"),
                rs.getString("description")
        );
    }
}