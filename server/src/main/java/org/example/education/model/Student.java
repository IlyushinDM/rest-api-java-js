package org.example.education.model;

public class Student {
    private int studentId;
    private String firstName;
    private String lastName;
    private String email;
    private String groupName;
    // Поле passwordHash не отдаем клиенту, оно только для сервера
    // private String passwordHash; // Убрали, чтобы случайно не сериализовать

    public Student() {}

    public Student(int studentId, String firstName, String lastName, String email, String groupName) {
        this.studentId = studentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.groupName = groupName;
    }

    // Геттеры и сеттеры
    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    // toString, equals, hashCode - опционально, но полезно
}