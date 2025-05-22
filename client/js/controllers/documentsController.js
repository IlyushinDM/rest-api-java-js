const DocumentsController = {
    // --- Студенческие функции ---
    myDocuments: async function() {
        if (!Auth.isStudent()) { AppRouter.navigateTo('/login'); return; }
        const userInfo = Auth.getUserInfo();
        if (!userInfo) { Auth.logout(); return; }
        try {
            AppRouter.appContentElement.innerHTML = '<p>Загрузка ваших документов...</p>';
            const documents = await ApiService.getDocumentsForStudent(userInfo.userId);
            AppRouter.appContentElement.innerHTML = DocumentsView.renderMyDocuments(documents);
            this.attachMyDocumentsEventListeners(userInfo.userId);
        } catch (error) {
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить документы: ${error.message}</p>`;
            DocumentsView.displayError('my-documents-error', `Не удалось загрузить документы: ${error.message}`);
        }
    },

    attachMyDocumentsEventListeners: function(studentId) {
        const uploadForm = document.getElementById('document-upload-form');
        if (uploadForm) {
            uploadForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                DocumentsView.displayError('document-upload-error', '');
                const fileInput = document.getElementById('docFile');
                if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
                    DocumentsView.displayError('document-upload-error', 'Пожалуйста, выберите файл для загрузки.');
                    return;
                }
                const formData = new FormData(uploadForm); // FormData сама соберет все поля формы, включая файл

                try {
                    AppRouter.appContentElement.querySelector('#document-upload-form-container button[type="submit"]').textContent = 'Загрузка...';
                    AppRouter.appContentElement.querySelector('#document-upload-form-container button[type="submit"]').disabled = true;

                    await ApiService.uploadDocument(studentId, formData);
                    alert('Документ успешно загружен!');
                    this.myDocuments(); // Обновить список
                } catch (error) {
                    DocumentsView.displayError('document-upload-error', `Ошибка загрузки: ${error.message}`);
                } finally {
                    const submitButton = AppRouter.appContentElement.querySelector('#document-upload-form-container button[type="submit"]');
                    if(submitButton) {
                        submitButton.textContent = 'Загрузить';
                        submitButton.disabled = false;
                    }
                }
            });
        }

        document.querySelectorAll('.download-document').forEach(button => {
            button.addEventListener('click', (e) => this.handleDownload(e.target.dataset.docId));
        });

        document.querySelectorAll('.delete-document').forEach(button => {
            button.addEventListener('click', async (e) => {
                const docId = e.target.dataset.docId;
                if (confirm('Вы уверены, что хотите удалить этот документ? Это действие необратимо.')) {
                    try {
                        await ApiService.deleteDocument(docId);
                        this.myDocuments();
                    } catch (error) {
                        DocumentsView.displayError('my-documents-error', `Ошибка удаления: ${error.message}`);
                    }
                }
            });
        });
    },

    // --- Админские функции ---
    listStudentDocumentsAdmin: async function(params) {
        if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
        if (!params || !params.studentId) {
            AppRouter.appContentElement.innerHTML = '<p class="error-message">ID студента не указан.</p>'; return;
        }
        const studentId = parseInt(params.studentId);
        try {
            AppRouter.appContentElement.innerHTML = `<p>Загрузка данных студента ID ${studentId}...</p>`;
            const student = await ApiService.getStudent(studentId);
            if (!student) throw new Error('Студент не найден');

            AppRouter.appContentElement.innerHTML = `<p>Загрузка документов студента ${student.firstName} ${student.lastName}...</p>`;
            const documents = await ApiService.getDocumentsForStudent(studentId);
            AppRouter.appContentElement.innerHTML = DocumentsView.renderAdminStudentDocuments(documents, student);
            this.attachAdminDocumentsEventListeners(studentId);
        } catch (error) {
             AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить документы студента: ${error.message}</p>`;
             DocumentsView.displayError('admin-student-documents-error', `Не удалось загрузить документы: ${error.message}`);
        }
    },

    attachAdminDocumentsEventListeners: function(studentIdContext) {
         document.querySelectorAll('.admin-download-document').forEach(button => {
            button.addEventListener('click', (e) => this.handleDownload(e.target.dataset.docId));
        });
        document.querySelectorAll('.admin-delete-document').forEach(button => {
            button.addEventListener('click', async (e) => {
                const docId = e.target.dataset.docId;
                 if (confirm(`АДМИН: Вы уверены, что хотите удалить документ ID ${docId} студента ID ${studentIdContext}?`)) {
                    try {
                        await ApiService.deleteDocument(docId);
                        this.listStudentDocumentsAdmin({studentId: studentIdContext.toString()});
                    } catch (error) {
                        DocumentsView.displayError('admin-student-documents-error', `Ошибка удаления документа: ${error.message}`);
                    }
                }
            });
        });

        const adminUploadForm = document.getElementById('admin-document-upload-form');
        if (adminUploadForm) {
            adminUploadForm.addEventListener('submit', async (e) => {
                e.preventDefault();
                DocumentsView.displayError('admin-document-upload-error', '');
                const fileInput = document.getElementById('adminDocFile');
                 if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
                    DocumentsView.displayError('admin-document-upload-error', 'Пожалуйста, выберите файл для загрузки.');
                    return;
                }
                const formData = new FormData(adminUploadForm);
                // studentIdForUploadByAdmin уже в formData из hidden input
                try {
                    adminUploadForm.querySelector('button[type="submit"]').textContent = 'Загрузка...';
                    adminUploadForm.querySelector('button[type="submit"]').disabled = true;

                    await ApiService.uploadDocument(studentIdContext, formData);
                    alert('Документ успешно загружен для студента!');
                    this.listStudentDocumentsAdmin({studentId: studentIdContext.toString()});
                } catch (error) {
                    DocumentsView.displayError('admin-document-upload-error', `Ошибка загрузки: ${error.message}`);
                } finally {
                    const submitButton = adminUploadForm.querySelector('button[type="submit"]');
                     if(submitButton) {
                        submitButton.textContent = 'Загрузить от имени студента';
                        submitButton.disabled = false;
                    }
                }
            });
        }
    },

    // Общий метод для скачивания файла (более безопасный вариант)
    handleDownload: async function(docId) {
        if (!docId) { alert('ID документа не указан.'); return; }
        try {
            const response = await fetch(`${clientConfig.serverUrl}/documents/${docId}/download`, {
                headers: { 'Authorization': `Bearer ${Auth.getToken()}` }
            });
            if (!response.ok) {
                let errorData;
                try { errorData = await response.json(); } catch (e) { /* ignore */ }
                throw new Error(errorData?.error || `Ошибка скачивания: ${response.status} ${response.statusText}`);
            }
            const blob = await response.blob();
            const disposition = response.headers.get('Content-Disposition');
            let filename = `document_${docId}.file`; // Имя по умолчанию
            if (disposition && disposition.includes('attachment')) {
                const filenameMatch = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/i);
                if (filenameMatch && filenameMatch[1]) {
                  filename = filenameMatch[1].replace(/['"]/g, '');
                  // Декодируем имя файла, если оно было закодировано (например, UTF-8 в URL-encoded виде)
                  try { filename = decodeURIComponent(filename); } catch(e) { console.warn("Could not decode filename", e); }
                }
            }

            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(link.href);
        } catch (error) {
            console.error("Download failed for doc ID:", docId, error);
            alert(`Не удалось скачать файл: ${error.message}`);
        }
    }
};