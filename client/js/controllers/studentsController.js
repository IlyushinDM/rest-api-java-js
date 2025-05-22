// Файл: educational-institution-client/js/controllers/studentsController.js

const StudentsController = {
    listStudents: async function() {
        if (!Auth.isAdmin()) {
            AppRouter.navigateTo(Auth.isStudent() ? '/dashboard' : '/login');
            return;
        }
        try {
            AppRouter.appContentElement.innerHTML = '<p>Загрузка списка студентов...</p>';
            const students = await ApiService.getStudents();
            AppRouter.appContentElement.innerHTML = StudentsView.renderList(students);
            this.attachAdminListEventListeners(); // Вызываем здесь, this будет StudentsController
        } catch (error) {
            console.error("Error in listStudents:", error);
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить список студентов: ${error.message}</p>`;
            StudentsView.displayError('students-list-error', `Ошибка: ${error.message}`);
        }
    },

    showStudentForm: async function(params) {
        if (!Auth.isAdmin()) {
            AppRouter.navigateTo(Auth.isStudent() ? '/dashboard' : '/login');
            return;
        }
        let studentData = {};
        const isEditMode = params && params.id;
        if (isEditMode) {
            try {
                AppRouter.appContentElement.innerHTML = '<p>Загрузка данных студента...</p>';
                studentData = await ApiService.getStudent(params.id);
            } catch (error) {
                console.error(`Error fetching student ${params.id} for edit:`, error);
                AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить данные студента: ${error.message}</p>`;
                return;
            }
        }
        AppRouter.appContentElement.innerHTML = StudentsView.renderForm(studentData, false); // false для forRegistration
        this.attachFormEventListeners(isEditMode); // this будет StudentsController
    },

    showStudentDetail: async function(params) {
         if (!params || !params.id) {
            AppRouter.appContentElement.innerHTML = `<p class="error-message">ID студента не указан.</p>`;
            return;
        }
        const studentIdToShow = parseInt(params.id);
        const currentUser = Auth.getUserInfo();

        let canView = false;
        if (Auth.isAdmin()) {
            canView = true;
        } else if (Auth.isStudent() && currentUser && currentUser.userId === studentIdToShow) {
            canView = true;
        }

        if (!canView) {
            AppRouter.appContentElement.innerHTML = StudentsView.renderDetail(null); // View сам обработает null
            console.warn(`Forbidden access to student detail ${studentIdToShow} by user ${currentUser ? currentUser.userId : 'Guest'}`);
            return;
        }

        try {
            AppRouter.appContentElement.innerHTML = `<p>Загрузка деталей студента ID ${studentIdToShow}...</p>`;
            const student = await ApiService.getStudent(studentIdToShow);
            AppRouter.appContentElement.innerHTML = StudentsView.renderDetail(student);
        } catch (error) {
             console.error(`Error fetching student detail ${studentIdToShow}:`, error);
             AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить детали студента: ${error.message}</p>`;
        }
    },

    attachAdminListEventListeners: function() {
        if (!Auth.isAdmin()) return;

        const addBtn = document.getElementById('add-student-btn');
        if (addBtn) {
            addBtn.addEventListener('click', () => {
                console.log("Add student button clicked");
                AppRouter.navigateTo('/admin/students/new');
            });
        } else {
            console.warn("'add-student-btn' not found");
        }

        document.querySelectorAll('.view-student-details.admin-action').forEach(button => {
            button.addEventListener('click', (e) => {
                const studentId = e.target.dataset.id;
                console.log(`View details clicked for student ID: ${studentId}`);
                AppRouter.navigateTo(`/admin/students/detail/${studentId}`);
            });
        });

        document.querySelectorAll('.edit-student.admin-action').forEach(button => {
            button.addEventListener('click', (e) => {
                const studentId = e.target.dataset.id;
                console.log(`Edit clicked for student ID: ${studentId}`);
                AppRouter.navigateTo(`/admin/students/edit/${studentId}`);
            });
        });

        document.querySelectorAll('.delete-student.admin-action').forEach(button => {
            button.addEventListener('click', async (e) => { // this здесь будет undefined, если не стрелочная функция
                const studentId = e.target.dataset.id;
                console.log(`Delete clicked for student ID: ${studentId}`);
                if (confirm(`АДМИН: Вы уверены, что хотите удалить студента с ID ${studentId}?`)) {
                    try {
                        await ApiService.deleteStudent(studentId);
                        // this.listStudents() здесь не сработает правильно, если это обычная функция
                        // Нужно вызывать StudentsController.listStudents() или передавать this
                        StudentsController.listStudents(); // Вызываем метод объекта напрямую
                    } catch (error) {
                        console.error(`Error deleting student ${studentId}:`, error);
                        StudentsView.displayError('students-list-error', `Ошибка удаления студента: ${error.message}`);
                    }
                }
            });
        });

        document.querySelectorAll('.manage-student-enrollments.admin-action').forEach(button => {
            button.addEventListener('click', (e) => {
                const studentId = e.target.dataset.id;
                console.log(`Manage enrollments clicked for student ID: ${studentId}`);
                AppRouter.navigateTo(`/admin/students/${studentId}/enrollments`);
            });
        });

        document.querySelectorAll('.manage-student-grades.admin-action').forEach(button => {
            button.addEventListener('click', (e) => {
                const studentId = e.target.dataset.id;
                console.log(`Manage grades clicked for student ID: ${studentId}`);
                AppRouter.navigateTo(`/admin/students/${studentId}/grades`);
            });
        });

        document.querySelectorAll('.manage-student-documents.admin-action').forEach(button => {
            button.addEventListener('click', (e) => {
                const studentId = e.target.dataset.id;
                console.log(`Manage documents clicked for student ID: ${studentId}`);
                AppRouter.navigateTo(`/admin/students/${studentId}/documents`);
            });
        });
    },

    attachFormEventListeners: function(isEditMode) {
        if (!Auth.isAdmin()) return;

        const form = document.getElementById('student-form');
        if (form) {
            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                StudentsView.displayError('student-form-error', '');
                const studentData = StudentsView.getFormData(isEditMode);

                if (!studentData.firstName || !studentData.lastName || !studentData.email) {
                     StudentsView.displayError('student-form-error', 'Имя, фамилия и email обязательны.');
                     return;
                }
                // Пароль для нового студента или новый пароль при редактировании
                const passwordToCheck = isEditMode ? studentData.newPassword : studentData.password;
                if (isEditMode && passwordToCheck && passwordToCheck.length < 5) {
                     StudentsView.displayError('student-form-error', 'Новый пароль должен быть не менее 5 символов.');
                     return;
                }
                 if (!isEditMode && (!passwordToCheck || passwordToCheck.length < 5)) {
                     StudentsView.displayError('student-form-error', 'Пароль обязателен и должен быть не менее 5 символов.');
                     return;
                }


                try {
                    if (isEditMode) {
                        await ApiService.updateStudent(studentData.studentId, studentData);
                    } else {
                        await ApiService.createStudent(studentData);
                    }
                    AppRouter.navigateTo('/admin/students');
                } catch (error) {
                     console.error("Error saving student form:", error);
                     StudentsView.displayError('student-form-error', `Ошибка сохранения: ${error.message}`);
                }
            });
            const cancelButton = document.getElementById('cancel-student-form');
            if (cancelButton) {
                cancelButton.addEventListener('click', () => AppRouter.navigateTo('/admin/students'));
            }
        } else {
            console.error("'student-form' not found for attaching event listeners.");
        }
    }
};