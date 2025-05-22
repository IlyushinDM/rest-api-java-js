package org.example.education.controller;

import org.example.education.model.Document;
import org.example.education.model.UserType; // Импорт
import org.example.education.service.DocumentService;
import org.example.education.util.JsonUtil;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.utils.IOUtils;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static spark.Spark.*;

public class DocumentController {
    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private final DocumentService documentService;

    private static final String TEMP_FILE_LOCATION = "./temp_uploads";
    private static final long MAX_FILE_SIZE = 1024 * 1024 * 5; // 5 MB
    private static final long MAX_REQUEST_SIZE = 1024 * 1024 * 6; // 6 MB
    private static final int FILE_SIZE_THRESHOLD = 1024 * 1024; // 1MB

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
        setupRoutes();
    }

    private boolean isAdmin(Request request) {
        UserType userType = request.attribute("userType");
        String userRole = request.attribute("userRole");
        return userType == UserType.EMPLOYEE && "ADMIN".equals(userRole);
    }

    private void setupRoutes() {
        File tempUploadDir = new File(TEMP_FILE_LOCATION);
        if (!tempUploadDir.exists()) {
            tempUploadDir.mkdirs();
        }

        post("/api/students/:studentId/documents/upload", "multipart/form-data", this::uploadDocumentFile, JsonUtil.jsonResponseTransformer());
        get("/api/students/:studentId/documents", this::getDocumentsForStudent, JsonUtil.jsonResponseTransformer());
        get("/api/documents/:documentId", this::getDocumentMetadataById, JsonUtil.jsonResponseTransformer());
        get("/api/documents/:documentId/download", this::downloadDocumentFile);
        put("/api/documents/:documentId", this::updateDocumentMetadata, JsonUtil.jsonResponseTransformer());
        delete("/api/documents/:documentId", this::deleteDocument, JsonUtil.jsonResponseTransformer());
    }

    private Object uploadDocumentFile(Request request, Response response) {
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int studentIdForDocument;
        try {
            studentIdForDocument = Integer.parseInt(request.params(":studentId"));
        } catch (NumberFormatException e) {
            auditLogger.warn("Invalid student ID format for document upload by User ID {}.", requestingUserId);
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid student ID format.")));
            return null;
        }

        auditLogger.info("User ID {} (Type: {}) attempting to upload document for student ID {}.",
                requestingUserId, requestingUserType, studentIdForDocument);

        boolean canUpload = false;
        if (requestingUserType == UserType.STUDENT && studentIdForDocument == requestingUserId) {
            canUpload = true; // Студент загружает для себя
        } else if (isAdmin(request)) {
            canUpload = true; // Админ может загружать для любого
        }

        if (!canUpload) {
            auditLogger.warn("Forbidden document upload attempt by User ID {} for student ID {}.", requestingUserId, studentIdForDocument);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: You do not have permission to upload documents for this student.")));
        }

        request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement(TEMP_FILE_LOCATION, MAX_FILE_SIZE, MAX_REQUEST_SIZE, FILE_SIZE_THRESHOLD));
        String documentName = request.queryParams("documentName");
        String documentType = request.queryParams("documentType");
        Part filePart = null;
        try {
            filePart = request.raw().getPart("file");
        } catch (IOException | ServletException e) {
            logger.error("Error getting file part for studentId {} by user {}", studentIdForDocument, requestingUserId, e);
            auditLogger.error("Document upload failed (Server Error - file part) for student ID {} by User ID {}: {}", studentIdForDocument, requestingUserId, e.getMessage());
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Error processing file upload.")));
        }

        if (filePart == null || filePart.getSize() == 0) { // Проверка, что файл не пустой
            auditLogger.warn("Document upload failed (Bad Request - no file part or empty file) for student ID {} by User ID {}.", studentIdForDocument, requestingUserId);
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "File part is missing or empty.")));
        }
        if (documentName == null || documentName.trim().isEmpty()) documentName = filePart.getSubmittedFileName();
        if (documentType == null || documentType.trim().isEmpty()) documentType = "Unspecified";

        try (InputStream inputStream = filePart.getInputStream()) {
            Document document = documentService.addDocumentWithFile(studentIdForDocument, documentName, documentType, inputStream, filePart.getSubmittedFileName());
            auditLogger.info("Document '{}' uploaded by User ID {} for student ID {}. Doc ID: {}", documentName, requestingUserId, studentIdForDocument, document.getDocumentId());
            response.status(HttpStatus.CREATED_201);
            return document;
        } catch (IOException | IllegalArgumentException e) { // IllegalArgumentException от DocumentService
            // ... (обработка ошибок как была) ...
            if (e instanceof IllegalArgumentException) {
                logger.warn("Invalid data for document upload for studentId {} by user {}: {}", studentIdForDocument, requestingUserId, e.getMessage());
                auditLogger.warn("Document upload failed (Bad Request/Not Found - student) for student ID {} by User ID {}: {}", studentIdForDocument, requestingUserId, e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage()))); // или 404 если студент не найден
            } else { // IOException
                logger.error("Error uploading document for studentId {} by user {}", studentIdForDocument, requestingUserId, e);
                auditLogger.error("Document upload failed (Server Error - IO) for student ID {} by User ID {}: {}", studentIdForDocument, requestingUserId, e.getMessage());
                halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Failed to upload document.")));
            }
        }
        return null;
    }


    private Object getDocumentsForStudent(Request request, Response response) {
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int studentIdFromPath;
        try {
            studentIdFromPath = Integer.parseInt(request.params(":studentId"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid student ID format.")));
            return null;
        }
        auditLogger.info("User ID {} (Type: {}) requesting documents for student ID: {}", requestingUserId, requestingUserType, studentIdFromPath);

        boolean canView = false;
        if (isAdmin(request)) {
            canView = true;
        } else if (requestingUserType == UserType.STUDENT && studentIdFromPath == requestingUserId) {
            canView = true;
        }

        if (!canView) {
            auditLogger.warn("Forbidden attempt by User ID {} to access documents for student ID {}.", requestingUserId, studentIdFromPath);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden: You can only view your own documents or have admin rights.")));
        }
        return documentService.getDocumentsForStudent(studentIdFromPath);
    }

    private Object getDocumentMetadataById(Request request, Response response) {
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int documentId;
        try {
            documentId = Integer.parseInt(request.params(":documentId"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid document ID format.")));
            return null;
        }
        auditLogger.info("User ID {} (Type: {}) requesting document metadata by ID: {}", requestingUserId, requestingUserType, documentId);

        Optional<Document> docOpt = documentService.getDocumentById(documentId);
        if (docOpt.isEmpty()) {
            halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Document not found.")));
        }

        Document document = docOpt.get();
        boolean canView = false;
        if(isAdmin(request)){
            canView = true;
        } else if (requestingUserType == UserType.STUDENT && document.getStudentId() == requestingUserId) {
            canView = true;
        }

        if (!canView) {
            auditLogger.warn("Forbidden attempt by User ID {} to access document metadata ID {}.", requestingUserId, documentId);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden to access this document.")));
        }
        return document;
    }

    private Object downloadDocumentFile(Request request, Response response) {
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int documentId;
        try {
            documentId = Integer.parseInt(request.params(":documentId"));
        } catch (NumberFormatException e) {
            auditLogger.warn("Invalid document ID format '{}' for download by User ID {}.", request.params(":documentId"), requestingUserId);
            response.status(HttpStatus.BAD_REQUEST_400);
            return JsonUtil.toJson(Collections.singletonMap("error", "Invalid document ID format.")); // Возвращаем JSON ошибку
        }
        auditLogger.info("User ID {} (Type: {}) attempting to download document file ID: {}", requestingUserId, requestingUserType, documentId);

        Optional<Document> docMetaOpt = documentService.getDocumentById(documentId);
        if (docMetaOpt.isEmpty()) {
            auditLogger.warn("Document ID {} not found for download by User ID {}.", documentId, requestingUserId);
            response.status(HttpStatus.NOT_FOUND_404);
            return JsonUtil.toJson(Collections.singletonMap("error", "Document metadata not found."));
        }
        Document docMeta = docMetaOpt.get();
        boolean canDownload = false;
        if(isAdmin(request)){
            canDownload = true;
        } else if (requestingUserType == UserType.STUDENT && docMeta.getStudentId() == requestingUserId) {
            canDownload = true;
        }

        if (!canDownload) {
            auditLogger.warn("Forbidden attempt by User ID {} to download document ID {}.", requestingUserId, documentId);
            response.status(HttpStatus.FORBIDDEN_403);
            return JsonUtil.toJson(Collections.singletonMap("error", "Forbidden to access this document file."));
        }

        Optional<File> fileOpt = documentService.getDocumentFile(documentId);
        if (fileOpt.isPresent()) {
            File file = fileOpt.get();
            try {
                response.header("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(file.getName(), "UTF-8").replace("+", "%20") + "\"");
                response.type(Files.probeContentType(file.toPath()));
                response.status(HttpStatus.OK_200);
                auditLogger.info("Successfully served file {} (Doc ID {}) for download to User ID {}.", file.getName(), documentId, requestingUserId);

                try (InputStream fileInputStream = Files.newInputStream(file.toPath())) {
                    IOUtils.copy(fileInputStream, response.raw().getOutputStream());
                    return response.raw();
                }
            } catch (IOException e) {
                logger.error("IO error during file download for doc ID {} by user {}: {}", documentId, requestingUserId, e.getMessage(), e);
                auditLogger.error("File download failed (Server Error - IO) for doc ID {} by User ID {}: {}", documentId, requestingUserId, e.getMessage());
                response.status(HttpStatus.INTERNAL_SERVER_ERROR_500);
                return JsonUtil.toJson(Collections.singletonMap("error", "Error serving file."));
            }
        } else {
            auditLogger.warn("File for document ID {} not found or not readable for download by User ID {}.", documentId, requestingUserId);
            response.status(HttpStatus.NOT_FOUND_404);
            return JsonUtil.toJson(Collections.singletonMap("error", "File not found or not accessible."));
        }
    }


    private Object updateDocumentMetadata(Request request, Response response) {
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int documentId;
        try {
            documentId = Integer.parseInt(request.params(":documentId"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid document ID format.")));
            return null;
        }
        auditLogger.info("User ID {} (Type: {}) attempting to update document metadata ID {}. Body: {}",
                requestingUserId, requestingUserType, documentId, request.body());

        Optional<Document> existingDocOpt = documentService.getDocumentById(documentId);
        if (existingDocOpt.isEmpty()) {
            halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Document not found.")));
        }

        Document existingDoc = existingDocOpt.get();
        boolean canUpdate = false;
        if(isAdmin(request)){ // Админ может обновлять любой документ (метаданные)
            canUpdate = true;
        } else if (requestingUserType == UserType.STUDENT && existingDoc.getStudentId() == requestingUserId) {
            canUpdate = true; // Студент может обновлять метаданные своего документа
        }

        if(!canUpdate){
            auditLogger.warn("Forbidden attempt by User ID {} to update document ID {}.", requestingUserId, documentId);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden to update this document.")));
        }

        try {
            Document documentData = JsonUtil.fromJson(request.body(), Document.class);
            // Устанавливаем student_id из существующего документа, чтобы пользователь не мог его поменять через тело запроса,
            // если только это не админ, который меняет принадлежность (но это более сложный сценарий).
            // Пока что предполагаем, что student_id не меняется при обновлении метаданных.
            documentData.setStudentId(existingDoc.getStudentId());

            if (documentService.updateDocumentMetadata(documentId, documentData)) {
                auditLogger.info("Document metadata ID {} updated successfully by User ID {}.", documentId, requestingUserId);
                response.status(HttpStatus.OK_200);
                return documentService.getDocumentById(documentId).orElse(null);
            } else {
                // ... (обработка ошибок как была) ...
                auditLogger.warn("Document metadata ID {} update failed by User ID {}.", documentId, requestingUserId);
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Document metadata update failed.")));
            }
        } catch (IllegalArgumentException e) { // Отдельный catch
            if (e.getMessage() != null && e.getMessage().contains("Invalid student reference")) {
                auditLogger.warn("Document update failed (Not Found - student ref) by User ID {}: {}", request.params(":documentId"), requestingUserId, e.getMessage());
                halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            } else {
                logger.warn("Invalid data for updating document ID {} by User ID {}: {}",request.params(":documentId"), requestingUserId, e.getMessage());
                auditLogger.warn("Document update failed (Bad Request) by User ID {}: {}",request.params(":documentId"), requestingUserId, e.getMessage());
                halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", e.getMessage())));
            }
        } catch (RuntimeException e) { // Общий catch
            logger.error("Error updating document metadata ID {} by User ID {}: {}",request.params(":documentId"), requestingUserId, e.getMessage(), e);
            auditLogger.error("Document update failed (Server Error) for ID {} by User ID {}: {}",request.params(":documentId"), requestingUserId, e.getMessage());
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error during document update.")));
        }
        return null;
    }

    private Object deleteDocument(Request request, Response response) {
        response.type("application/json");
        Integer requestingUserId = request.attribute("userId");
        UserType requestingUserType = request.attribute("userType");
        int documentId;
        try {
            documentId = Integer.parseInt(request.params(":documentId"));
        } catch (NumberFormatException e) {
            halt(HttpStatus.BAD_REQUEST_400, JsonUtil.toJson(Collections.singletonMap("error", "Invalid document ID format.")));
            return null;
        }
        auditLogger.info("User ID {} (Type: {}) attempting to delete document ID: {}", requestingUserId, requestingUserType, documentId);

        Optional<Document> docOpt = documentService.getDocumentById(documentId);
        if (docOpt.isEmpty()) {
            halt(HttpStatus.NOT_FOUND_404, JsonUtil.toJson(Collections.singletonMap("error", "Document not found.")));
        }

        boolean canDelete = false;
        if(isAdmin(request)){
            canDelete = true;
        } else if (requestingUserType == UserType.STUDENT && docOpt.get().getStudentId() == requestingUserId) {
            canDelete = true;
        }

        if(!canDelete){
            auditLogger.warn("Forbidden attempt by User ID {} to delete document ID {}.", requestingUserId, documentId);
            halt(HttpStatus.FORBIDDEN_403, JsonUtil.toJson(Collections.singletonMap("error", "Forbidden to delete this document.")));
        }

        try {
            if (documentService.deleteDocument(documentId)) {
                auditLogger.info("Document ID {} deleted successfully by User ID {}.", documentId, requestingUserId);
                response.status(HttpStatus.NO_CONTENT_204);
                return "";
            } else {
                auditLogger.warn("Document ID {} deletion failed or document not found for request by User ID {}.", documentId, requestingUserId);
                response.status(HttpStatus.NOT_FOUND_404); // Может быть, если документ уже удален
                return Collections.singletonMap("error", "Document not found or delete failed.");
            }
        } catch (Exception e) {
            logger.error("Error deleting document ID {} by User ID {}: {}",documentId, requestingUserId, e.getMessage(), e);
            auditLogger.error("Document delete failed (Server Error) for ID {} by User ID {}: {}",documentId, requestingUserId, e.getMessage());
            halt(HttpStatus.INTERNAL_SERVER_ERROR_500, JsonUtil.toJson(Collections.singletonMap("error", "Internal server error during document deletion.")));
        }
        return null;
    }
}