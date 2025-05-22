package org.example.education.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
// Примечание: @JsonFormat используется для корректной сериализации/десериализации дат Java Time API в JSON.
public class Document {
    private int documentId;
    private int studentId;
    private String documentName;
    private String documentType;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime uploadDate;
    private String filePath; // Пока просто строка, загрузка файлов - отдельная тема

    public Document() {}

    public Document(int documentId, int studentId, String documentName, String documentType, LocalDateTime uploadDate, String filePath) {
        this.documentId = documentId;
        this.studentId = studentId;
        this.documentName = documentName;
        this.documentType = documentType;
        this.uploadDate = uploadDate;
        this.filePath = filePath;
    }

    // Геттеры и сеттеры
    public int getDocumentId() { return documentId; }
    public void setDocumentId(int documentId) { this.documentId = documentId; }
    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public LocalDateTime getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDateTime uploadDate) { this.uploadDate = uploadDate; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}