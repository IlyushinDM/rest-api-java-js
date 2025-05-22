package org.example.education.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public class Grade {
    private int gradeId;
    private int studentCourseId; // Ссылка на student_courses
    private String gradeValue;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate gradeDate;
    private String comments;

    public Grade() {}

    public Grade(int gradeId, int studentCourseId, String gradeValue, LocalDate gradeDate, String comments) {
        this.gradeId = gradeId;
        this.studentCourseId = studentCourseId;
        this.gradeValue = gradeValue;
        this.gradeDate = gradeDate;
        this.comments = comments;
    }

    // Геттеры и сеттеры
    public int getGradeId() { return gradeId; }
    public void setGradeId(int gradeId) { this.gradeId = gradeId; }
    public int getStudentCourseId() { return studentCourseId; }
    public void setStudentCourseId(int studentCourseId) { this.studentCourseId = studentCourseId; }
    public String getGradeValue() { return gradeValue; }
    public void setGradeValue(String gradeValue) { this.gradeValue = gradeValue; }
    public LocalDate getGradeDate() { return gradeDate; }
    public void setGradeDate(LocalDate gradeDate) { this.gradeDate = gradeDate; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}