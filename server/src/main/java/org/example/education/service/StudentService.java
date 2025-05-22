package org.example.education.service;

import org.example.education.dao.StudentDao;
import org.example.education.model.Student;
import org.example.education.util.PasswordUtil; // Убедитесь, что импортирован
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class StudentService {
    private static final Logger logger = LoggerFactory.getLogger(StudentService.class);
    private final StudentDao studentDao;

    public StudentService(StudentDao studentDao) {
        this.studentDao = studentDao;
    }

    public Optional<Student> getStudentById(int id) {
        logger.debug("Fetching student by ID: {}", id);
        return studentDao.findById(id);
    }

    public List<Student> getAllStudents() {
        logger.debug("Fetching all students");
        return studentDao.findAll();
    }

    public Student createStudent(Student student, String rawPassword) {
        if (student == null || student.getEmail() == null || student.getEmail().trim().isEmpty() ||
                student.getFirstName() == null || student.getFirstName().trim().isEmpty() ||
                student.getLastName() == null || student.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Student email, first name, and last name are required.");
        }
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required for a new student.");
        }
        if (rawPassword.length() < 5) { // Пример валидации
            throw new IllegalArgumentException("Password must be at least 5 characters long.");
        }

        logger.info("Attempting to create student with email: {}", student.getEmail());
        // DAO отвечает за хеширование при создании (согласно последней версии StudentDao.save)
        return studentDao.save(student, rawPassword);
    }

    /**
     * Обновляет данные студента.
     * @param id ID студента для обновления.
     * @param studentData Объект Student с новыми данными (кроме пароля).
     * @param newRawPassword Новый сырой пароль. Если null или пустой, пароль не меняется.
     * @return true если обновление успешно, иначе false.
     */
    public boolean updateStudent(int id, Student studentData, String newRawPassword) {
        if (studentData == null) {
            throw new IllegalArgumentException("Student data cannot be null for update.");
        }
        // Убедимся, что ID в объекте studentData соответствует ID из пути (или устанавливаем его)
        if (studentData.getStudentId() == 0) {
            studentData.setStudentId(id);
        } else if (studentData.getStudentId() != id) {
            logger.warn("Mismatch in student ID for update. Path ID: {}, StudentData ID: {}. Using path ID.", id, studentData.getStudentId());
            studentData.setStudentId(id); // Приоритет у ID из пути
        }

        // Валидация основных данных студента
        if (studentData.getFirstName() == null || studentData.getFirstName().trim().isEmpty() ||
                studentData.getLastName() == null || studentData.getLastName().trim().isEmpty() ||
                studentData.getEmail() == null || studentData.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("First name, last name, and email cannot be empty for update.");
        }

        String newPasswordHash = null; // По умолчанию пароль не меняется
        boolean passwordChangeAttempted = (newRawPassword != null && !newRawPassword.trim().isEmpty());

        if (passwordChangeAttempted) {
            if (newRawPassword.length() < 5) { // Пример валидации длины нового пароля
                throw new IllegalArgumentException("New password must be at least 5 characters long.");
            }
            newPasswordHash = PasswordUtil.hashPassword(newRawPassword); // Хешируем сырой пароль
            logger.info("Attempting to update student ID: {} with new password.", id);
        } else {
            logger.info("Attempting to update student ID: {} (password not changed).", id);
        }

        return studentDao.update(studentData, newPasswordHash); // Передаем studentData и хеш (или null) в DAO
    }

    public boolean deleteStudent(int id) {
        logger.info("Attempting to delete student ID: {}", id);
        // Дополнительные проверки бизнес-логики перед удалением (например, есть ли активные записи)
        // могут быть здесь, но в данном случае просто делегируем DAO.
        return studentDao.delete(id);
    }
}