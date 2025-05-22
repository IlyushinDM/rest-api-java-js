package org.example.education.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public class StudentCourse {
    private int studentCourseId;
    private int studentId;
    private int courseId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate enrollmentDate;

    public StudentCourse() {}

    public StudentCourse(int studentCourseId, int studentId, int courseId, LocalDate enrollmentDate) {
        this.studentCourseId = studentCourseId;
        this.studentId = studentId;
        this.courseId = courseId;
        this.enrollmentDate = enrollmentDate;
    }

    // Геттеры и сеттеры
    public int getStudentCourseId() { return studentCourseId; }
    public void setStudentCourseId(int studentCourseId) { this.studentCourseId = studentCourseId; }
    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }
    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }
    public LocalDate getEnrollmentDate() { return enrollmentDate; }
    public void setEnrollmentDate(LocalDate enrollmentDate) { this.enrollmentDate = enrollmentDate; }
}