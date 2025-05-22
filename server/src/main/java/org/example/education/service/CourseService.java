package org.example.education.service;

import org.example.education.dao.CourseDao;
import org.example.education.model.Course;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class CourseService {
    private static final Logger logger = LoggerFactory.getLogger(CourseService.class);
    private final CourseDao courseDao;

    public CourseService(CourseDao courseDao) {
        this.courseDao = courseDao;
    }

    public Course createCourse(Course course) {
        // Дополнительная валидация, если нужна
        if (course.getCourseName() == null || course.getCourseName().trim().isEmpty()) {
            throw new IllegalArgumentException("Course name cannot be empty.");
        }
        logger.info("Attempting to create course: {}", course.getCourseName());
        return courseDao.save(course);
    }

    public Optional<Course> getCourseById(int courseId) {
        logger.debug("Fetching course by ID: {}", courseId);
        return courseDao.findById(courseId);
    }

    public List<Course> getAllCourses() {
        logger.debug("Fetching all courses");
        return courseDao.findAll();
    }

    public boolean updateCourse(int courseId, Course course) {
        if (course.getCourseName() == null || course.getCourseName().trim().isEmpty()) {
            throw new IllegalArgumentException("Course name cannot be empty.");
        }
        course.setCourseId(courseId); // Убедимся, что ID правильный
        logger.info("Attempting to update course ID: {}", courseId);
        return courseDao.update(course);
    }

    public boolean deleteCourse(int courseId) {
        logger.info("Attempting to delete course ID: {}", courseId);
        // Можно добавить проверку, например, можно ли удалять курс, если на нем есть студенты
        return courseDao.delete(courseId);
    }
}