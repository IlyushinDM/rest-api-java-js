package org.example.education.model;

public class Employee {
    private int employeeId;
    private String firstName;
    private String lastName;
    private String email;
    // passwordHash не отдаем клиенту
    private String role; // ADMIN, TEACHER, etc.

    public Employee() {}

    public Employee(int employeeId, String firstName, String lastName, String email, String role) {
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
    }

    // Геттеры и сеттеры
    public int getEmployeeId() { return employeeId; }
    public void setEmployeeId(int employeeId) { this.employeeId = employeeId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}