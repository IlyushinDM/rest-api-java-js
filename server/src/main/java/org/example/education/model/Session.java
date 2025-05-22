package org.example.education.model;

public class Session {
    private String token;
    private int userId; // ID студента или сотрудника
    private String userEmail;
    private UserType userType; // STUDENT или EMPLOYEE
    private String role;       // Например, "ADMIN", "TEACHER", или "STUDENT_ROLE"
    private long expiresAt;

    public Session(String token, int userId, String userEmail, UserType userType, String role, long expiresAt) {
        this.token = token;
        this.userId = userId;
        this.userEmail = userEmail;
        this.userType = userType;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    // Геттеры
    public String getToken() { return token; }
    public int getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public UserType getUserType() { return userType; }
    public String getRole() { return role; }
    public long getExpiresAt() { return expiresAt; }
}