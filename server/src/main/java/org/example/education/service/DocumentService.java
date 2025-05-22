package org.example.education.service;

import org.example.education.dao.DocumentDao;
import org.example.education.dao.StudentDao;
import org.example.education.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File; // Для работы с файлами
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DocumentService {
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    private final DocumentDao documentDao;
    private final StudentDao studentDao; // Для проверки существования студента

    // Путь для сохранения загруженных файлов (нужно будет настроить в server.properties)
    private final String UPLOAD_DIR = "./uploads/documents/"; // Пример

    public DocumentService(DocumentDao documentDao, StudentDao studentDao) {
        this.documentDao = documentDao;
        this.studentDao = studentDao;
        // Создать директорию для загрузок, если ее нет
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            logger.error("Could not create upload directory: {}", UPLOAD_DIR, e);
            // Можно кинуть исключение, чтобы сервер не стартовал без директории
        }
    }

    // Метод для добавления документа с загрузкой файла
    public Document addDocumentWithFile(int studentId, String documentName, String documentType, InputStream fileInputStream, String originalFileName) throws IOException {
        studentDao.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + studentId));

        // Генерируем уникальное имя файла, чтобы избежать коллизий
        String fileExtension = "";
        int i = originalFileName.lastIndexOf('.');
        if (i > 0) {
            fileExtension = originalFileName.substring(i);
        }
        String storedFileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = Paths.get(UPLOAD_DIR, String.valueOf(studentId), storedFileName); // Сохраняем в подпапку студента

        Files.createDirectories(filePath.getParent()); // Убедимся, что директория студента существует

        // Сохраняем файл
        Files.copy(fileInputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        logger.info("File {} uploaded successfully for student ID {} to {}", originalFileName, studentId, filePath.toString());

        Document document = new Document();
        document.setStudentId(studentId);
        document.setDocumentName(documentName); // Может быть задано пользователем или взято из имени файла
        document.setDocumentType(documentType);
        document.setUploadDate(LocalDateTime.now());
        document.setFilePath(filePath.toString()); // Сохраняем относительный или абсолютный путь

        return documentDao.save(document);
    }

    // Метод для добавления только метаданных документа (если файл уже где-то есть)
    public Document addDocumentMetadata(Document document) {
        if (document.getStudentId() <= 0) {
            throw new IllegalArgumentException("Valid studentId is required.");
        }
        studentDao.findById(document.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        if (document.getDocumentName() == null || document.getDocumentName().trim().isEmpty()) {
            throw new IllegalArgumentException("Document name cannot be empty.");
        }
        if (document.getFilePath() == null || document.getFilePath().trim().isEmpty()) {
            // Можно сделать необязательным, если файл не предполагается
            throw new IllegalArgumentException("File path cannot be empty if a file is associated.");
        }
        if (document.getUploadDate() == null) {
            document.setUploadDate(LocalDateTime.now());
        }
        logger.info("Attempting to add document metadata for student ID {}.", document.getStudentId());
        return documentDao.save(document);
    }


    public Optional<Document> getDocumentById(int documentId) {
        logger.debug("Fetching document by ID: {}", documentId);
        return documentDao.findById(documentId);
    }

    public List<Document> getDocumentsForStudent(int studentId) {
        logger.debug("Fetching documents for student ID: {}", studentId);
        return documentDao.findByStudentId(studentId);
    }

    public boolean updateDocumentMetadata(int documentId, Document documentData) {
        documentData.setDocumentId(documentId);
        if (documentData.getStudentId() <= 0) {
            throw new IllegalArgumentException("Valid studentId is required.");
        }
        studentDao.findById(documentData.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found for document update."));

        // Если filePath меняется, старый файл не удаляется автоматически этой логикой.
        logger.info("Attempting to update document metadata for ID: {}", documentId);
        return documentDao.update(documentData);
    }

    public boolean deleteDocument(int documentId) {
        logger.info("Attempting to delete document ID: {}", documentId);
        Optional<Document> docOpt = documentDao.findById(documentId);
        if (docOpt.isPresent()) {
            // Удаляем файл с диска
            try {
                Path filePath = Paths.get(docOpt.get().getFilePath());
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    logger.info("File {} deleted from disk.", filePath.toString());
                } else {
                    logger.warn("File {} not found on disk for document ID {}, but deleting DB record.", docOpt.get().getFilePath(), documentId);
                }
            } catch (IOException e) {
                logger.error("Error deleting file {} for document ID {}: {}", docOpt.get().getFilePath(), documentId, e.getMessage(), e);
                // Решаем, продолжать ли удаление из БД, если файл не удален.
                // Для простоты, продолжим. В продакшене это может быть транзакционная операция.
            }
            return documentDao.delete(documentId);
        } else {
            logger.warn("Delete document attempt failed: Document ID {} not found.", documentId);
            return false;
        }
    }

    // Метод для получения файла (если потребуется отдавать файл через API)
    public Optional<File> getDocumentFile(int documentId) {
        Optional<Document> docOpt = documentDao.findById(documentId);
        if (docOpt.isPresent()) {
            Path filePath = Paths.get(docOpt.get().getFilePath());
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                return Optional.of(filePath.toFile());
            } else {
                logger.warn("File not found or not readable at path: {} for document ID {}", filePath, documentId);
            }
        }
        return Optional.empty();
    }
}