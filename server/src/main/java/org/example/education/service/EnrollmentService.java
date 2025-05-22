package org.example.education.service;

import org.example.education.dao.CourseDao;
import org.example.education.dao.EnrollmentDao;
import org.example.education.dao.StudentDao;
import org.example.education.model.StudentCourse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class EnrollmentService {
    private static final Logger logger = LoggerFactory.getLogger(EnrollmentService.class);
    private final EnrollmentDao enrollmentDao;
    private final StudentDao studentDao; // Для проверки существования студента
    private final CourseDao courseDao;   // Для проверки существования курса

    public EnrollmentService(EnrollmentDao enrollmentDao, StudentDao studentDao, CourseDao courseDao) {
        this.enrollmentDao = enrollmentDao;
        this.studentDao = studentDao;
        this.courseDao = courseDao;
    }

    public StudentCourse enrollStudent(int studentId, int courseId) {
        // Проверяем, существуют ли студент и курс
        if (studentDao.findById(studentId).isEmpty()) {
            logger.warn("Enrollment attempt failed: Student with ID {} not found.", studentId);
            throw new IllegalArgumentException("Student not found.");
        }
        if (courseDao.findById(courseId).isEmpty()) {
            logger.warn("Enrollment attempt failed: Course with ID {} not found.", courseId);
            throw new IllegalArgumentException("Course not found.");
        }

        logger.info("Attempting to enroll student {} in course {}", studentId, courseId);
        return enrollmentDao.enrollStudent(studentId, courseId, LocalDate.now());
    }

    public boolean unenrollStudent(int studentCourseId) {
        logger.info("Attempting to unenroll student by enrollment ID {}", studentCourseId);
        // Проверка, существует ли такая запись, перед удалением
        if (enrollmentDao.findById(studentCourseId).isEmpty()) {
            logger.warn("Unenrollment attempt failed: Enrollment ID {} not found.", studentCourseId);
            return false; // или throw new EntityNotFoundException("Enrollment not found");
        }
        return enrollmentDao.unenrollStudent(studentCourseId);
    }

    public Optional<StudentCourse> getEnrollmentById(int studentCourseId) {
        logger.debug("Fetching enrollment by ID: {}", studentCourseId);
        return enrollmentDao.findById(studentCourseId);
    }

    public List<StudentCourse> getEnrollmentsForStudent(int studentId) {
        logger.debug("Fetching enrollments for student ID: {}", studentId);
        return enrollmentDao.findByStudentId(studentId);
    }

    public List<StudentCourse> getEnrollmentsForCourse(int courseId) {
        logger.debug("Fetching enrollments for course ID: {}", courseId);
        return enrollmentDao.findByCourseId(courseId);
    }

    public List<StudentCourse> getAllEnrollments() { // Новый метод
        logger.debug("Fetching all enrollments (admin action)");
        return enrollmentDao.findAll();
    }
}