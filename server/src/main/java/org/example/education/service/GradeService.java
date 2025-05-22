package org.example.education.service;

import org.example.education.dao.EnrollmentDao;
import org.example.education.dao.GradeDao;
import org.example.education.model.Grade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class GradeService {
    private static final Logger logger = LoggerFactory.getLogger(GradeService.class);
    private final GradeDao gradeDao;
    private final EnrollmentDao enrollmentDao; // Для проверки существования student_course_id

    public GradeService(GradeDao gradeDao, EnrollmentDao enrollmentDao) {
        this.gradeDao = gradeDao;
        this.enrollmentDao = enrollmentDao;
    }

    public Grade addGrade(Grade grade) {
        if (grade.getStudentCourseId() <= 0) {
            throw new IllegalArgumentException("Valid studentCourseId is required.");
        }
        if (grade.getGradeValue() == null || grade.getGradeValue().trim().isEmpty()) {
            throw new IllegalArgumentException("Grade value cannot be empty.");
        }
        // Проверяем, существует ли такая запись student_courses
        enrollmentDao.findById(grade.getStudentCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Enrollment record (student_course_id) not found."));

        if (grade.getGradeDate() == null) {
            grade.setGradeDate(LocalDate.now());
        }
        logger.info("Attempting to add grade for enrollment ID {}.", grade.getStudentCourseId());
        return gradeDao.save(grade);
    }

    public Optional<Grade> getGradeById(int gradeId) {
        logger.debug("Fetching grade by ID: {}", gradeId);
        return gradeDao.findById(gradeId);
    }

    public List<Grade> getGradesForEnrollment(int studentCourseId) {
        logger.debug("Fetching grades for enrollment ID: {}", studentCourseId);
        return gradeDao.findByStudentCourseId(studentCourseId);
    }

    public List<Grade> getGradesForStudent(int studentId) {
        logger.debug("Fetching all grades for student ID: {}", studentId);
        return gradeDao.findByStudentId(studentId);
    }

    public boolean updateGrade(int gradeId, Grade grade) {
        grade.setGradeId(gradeId); // Убедимся, что ID правильный
        if (grade.getStudentCourseId() <= 0) {
            throw new IllegalArgumentException("Valid studentCourseId is required.");
        }
        if (grade.getGradeValue() == null || grade.getGradeValue().trim().isEmpty()) {
            throw new IllegalArgumentException("Grade value cannot be empty.");
        }
        enrollmentDao.findById(grade.getStudentCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Enrollment record (student_course_id) not found for update."));

        if (grade.getGradeDate() == null) {
            grade.setGradeDate(LocalDate.now());
        }
        logger.info("Attempting to update grade ID: {}", gradeId);
        return gradeDao.update(grade);
    }

    public boolean deleteGrade(int gradeId) {
        logger.info("Attempting to delete grade ID: {}", gradeId);
        if (gradeDao.findById(gradeId).isEmpty()) {
            logger.warn("Delete grade attempt failed: Grade ID {} not found.", gradeId);
            return false;
        }
        return gradeDao.delete(gradeId);
    }
}