const DocumentsView = {
    // Для студента: список его документов и форма загрузки
    renderMyDocuments: function(documents) {
        if (!Auth.isStudent()) return '<p class="info-message">Эта страница предназначена для студентов.</p>';
        const userInfo = Auth.getUserInfo();
        if (!userInfo) { Auth.logout(); return '<p class="error-message">Ошибка: информация о пользователе не найдена.</p>'; }

        let html = '<h2>Мои документы</h2>';
        html += '<div id="my-documents-error" class="error-message"></div>';

        html += `
            <div id="document-upload-form-container" class="form-container">
                <h3>Загрузить новый документ</h3>
                <form id="document-upload-form" enctype="multipart/form-data">
                    <!-- studentId будет взят из Auth.getUserInfo().userId в контроллере -->
                    <div class="form-group">
                        <label for="docName">Название документа (если оставить пустым, будет использовано имя файла):</label>
                        <input type="text" id="docName" name="documentName" placeholder="например, Заявление на отпуск">
                    </div>
                    <div class="form-group">
                        <label for="docType">Тип документа:</label>
                        <input type="text" id="docType" name="documentType" placeholder="например, Заявление, Справка, Реферат" required>
                    </div>
                    <div class="form-group">
                        <label for="docFile">Файл:</label>
                        <input type="file" id="docFile" name="file" required>
                    </div>
                    <p id="document-upload-error" class="error-message"></p>
                    <button type="submit">Загрузить</button>
                </form>
            </div>
            <hr/>
        `;

        if (!documents || documents.length === 0) {
            html += '<p>У вас пока нет загруженных документов.</p>';
        } else {
            html += '<h3>Список ваших документов:</h3><ul class="document-list">';
            documents.forEach(doc => {
                html += `<li class="document-list-item">
                            <div class="doc-info">
                                <strong>${doc.documentName}</strong><br>
                                <small>Тип: ${doc.documentType}, Загружен: ${new Date(doc.uploadDate).toLocaleString()}</small><br>
                                <small>Имя файла: ${doc.filePath.split(/[\\/]/).pop()}</small>
                            </div>
                            <div class="doc-actions">
                                <button class="download-document" data-doc-id="${doc.documentId}" title="Скачать документ">Скачать</button>
                                <button class="delete-document danger" data-doc-id="${doc.documentId}" title="Удалить документ">Удалить</button>
                            </div>
                         </li>`;
            });
            html += '</ul>';
        }
        return html;
    },

    // Для админа: список документов студента и форма загрузки от его имени
    renderAdminStudentDocuments: function(documents, student) {
        if (!Auth.isAdmin()) return '<p class="error-message">Доступ запрещен.</p>';
        if (!student) return '<p class="error-message">Информация о студенте не передана.</p>';

        let html = `<h2>Документы студента: ${student.firstName} ${student.lastName} (ID: ${student.studentId})</h2>`;
        html += `<div id="admin-student-documents-error" class="error-message"></div>`;

        html += `
            <div id="admin-document-upload-form-container" class="form-container">
                <h3>Загрузить документ для этого студента (Админ)</h3>
                <form id="admin-document-upload-form" enctype="multipart/form-data">
                    <input type="hidden" name="studentIdForUploadByAdmin" value="${student.studentId}">
                    <div class="form-group">
                        <label for="adminDocName">Название документа (если оставить пустым, будет использовано имя файла):</label>
                        <input type="text" id="adminDocName" name="documentName" placeholder="например, Заявление на отпуск">
                    </div>
                    <div class="form-group">
                        <label for="adminDocType">Тип документа:</label>
                        <input type="text" id="adminDocType" name="documentType" placeholder="например, Заявление, Справка, Реферат" required>
                    </div>
                    <div class="form-group">
                        <label for="adminDocFile">Файл:</label>
                        <input type="file" id="adminDocFile" name="file" required>
                    </div>
                    <p id="admin-document-upload-error" class="error-message"></p>
                    <button type="submit">Загрузить от имени студента</button>
                </form>
            </div>
            <hr/>
        `;

        if (!documents || documents.length === 0) {
            html += '<p>У студента нет загруженных документов.</p>';
        } else {
            html += '<h3>Список документов студента:</h3><ul class="document-list">';
            documents.forEach(doc => {
                html += `<li class="document-list-item">
                            <div class="doc-info">
                                <strong>${doc.documentName}</strong><br>
                                <small>Тип: ${doc.documentType}, Загружен: ${new Date(doc.uploadDate).toLocaleString()}</small><br>
                                <small>Имя файла: ${doc.filePath.split(/[\\/]/).pop()}</small>
                            </div>
                            <div class="doc-actions">
                                <button class="admin-download-document" data-doc-id="${doc.documentId}" title="Скачать документ">Скачать</button>
                                <button class="admin-delete-document danger" data-doc-id="${doc.documentId}" title="Удалить документ">Удалить</button>
                            </div>
                         </li>`;
            });
            html += '</ul>';
        }
        html += `<hr/><button onclick="AppRouter.navigateTo('/admin/students/detail/${student.studentId}')">К профилю студента</button>`;
        return html;
    },

    displayError: function(elementId, message) {
        const errorElement = document.getElementById(elementId);
        if(errorElement) errorElement.textContent = message;
    }
};