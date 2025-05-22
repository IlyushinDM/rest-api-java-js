package org.example.education.dao;

import org.example.education.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DocumentDao {
    private static final Logger logger = LoggerFactory.getLogger(DocumentDao.class);

    public Document save(Document document) {
        String sql = "INSERT INTO documents (student_id, document_name, document_type, upload_date, file_path) VALUES (?, ?, ?, ?, ?) RETURNING document_id";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, document.getStudentId());
            pstmt.setString(2, document.getDocumentName());
            pstmt.setString(3, document.getDocumentType());
            pstmt.setTimestamp(4, document.getUploadDate() != null ? Timestamp.valueOf(document.getUploadDate()) : Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setString(5, document.getFilePath()); // Путь к файлу

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating document record failed, no rows affected.");
            }
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    document.setDocumentId(generatedKeys.getInt(1));
                    logger.info("Document record saved for student ID {}. Doc ID: {}", document.getStudentId(), document.getDocumentId());
                    return document;
                } else {
                    throw new SQLException("Creating document record failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            if (e.getSQLState().startsWith("23")) {
                logger.warn("Failed to save document due to data integrity violation (e.g., student_id not found): {}", e.getMessage());
                throw new RuntimeException("Cannot save document: Invalid student reference.", e);
            }
            logger.error("Error saving document record for student ID {}: {}", document.getStudentId(), e.getMessage(), e);
            throw new RuntimeException("Could not save document record: " + e.getMessage(), e);
        }
    }

    public Optional<Document> findById(int documentId) {
        String sql = "SELECT document_id, student_id, document_name, document_type, upload_date, file_path FROM documents WHERE document_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, documentId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRowToDocument(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding document by ID {}: {}", documentId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    public List<Document> findByStudentId(int studentId) {
        List<Document> documents = new ArrayList<>();
        String sql = "SELECT document_id, student_id, document_name, document_type, upload_date, file_path FROM documents WHERE student_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, studentId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                documents.add(mapRowToDocument(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding documents for student ID {}: {}", studentId, e.getMessage(), e);
        }
        return documents;
    }

    public boolean update(Document document) {
        // Обновление filePath может быть частью этого, или отдельной операцией, если файл перезаливается
        String sql = "UPDATE documents SET student_id = ?, document_name = ?, document_type = ?, upload_date = ?, file_path = ? WHERE document_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, document.getStudentId());
            pstmt.setString(2, document.getDocumentName());
            pstmt.setString(3, document.getDocumentType());
            pstmt.setTimestamp(4, document.getUploadDate() != null ? Timestamp.valueOf(document.getUploadDate()) : Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setString(5, document.getFilePath());
            pstmt.setInt(6, document.getDocumentId());
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Document ID {} updated successfully.", document.getDocumentId());
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            if (e.getSQLState().startsWith("23")) {
                logger.warn("Failed to update document due to data integrity violation: {}", e.getMessage());
                throw new RuntimeException("Cannot update document: Invalid student reference.", e);
            }
            logger.error("Error updating document ID {}: {}", document.getDocumentId(), e.getMessage(), e);
            return false;
        }
    }

    public boolean delete(int documentId) {
        // При удалении записи из БД, нужно также удалить сам файл с диска (если он там есть).
        // Это должно быть частью логики сервиса.
        String sql = "DELETE FROM documents WHERE document_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, documentId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Document record ID {} deleted successfully.", documentId);
            }
            return affectedRows > 0;
        } catch (SQLException e) {
            logger.error("Error deleting document record ID {}: {}", documentId, e.getMessage(), e);
            return false;
        }
    }

    private Document mapRowToDocument(ResultSet rs) throws SQLException {
        Timestamp uploadTimestamp = rs.getTimestamp("upload_date");
        LocalDateTime uploadDateTime = (uploadTimestamp != null) ? uploadTimestamp.toLocalDateTime() : null;
        return new Document(
                rs.getInt("document_id"),
                rs.getInt("student_id"),
                rs.getString("document_name"),
                rs.getString("document_type"),
                uploadDateTime,
                rs.getString("file_path")
        );
    }
}