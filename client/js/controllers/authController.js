const AuthController = {
    login: function() {
        AppRouter.appContentElement.innerHTML = LoginView.render();
        const form = document.getElementById('login-form');
        if (form) {
            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                LoginView.displayError('');
                const { email, password } = LoginView.getCredentials();
                if (!email || !password) {
                    LoginView.displayError('Email и пароль обязательны.');
                    return;
                }
                await Auth.login(email, password);
            });
        } else {
            console.error("Login form not found after rendering LoginView.");
        }
    },

    register: function() {
        // Передаем forRegistration = true
        AppRouter.appContentElement.innerHTML = StudentsView.renderForm({}, true);

        // Ищем h2 внутри загруженного контента #app-content
        const titleElement = AppRouter.appContentElement.querySelector('h2');
        if (titleElement) {
            // titleElement.textContent = 'Регистрация нового студента'; // Уже должно быть установлено во View
        } else {
            console.warn("Could not find h2 element to update title for registration form.");
        }

        const form = document.getElementById('student-form'); // Этот ID из StudentsView.renderForm
        if (form) {
            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                StudentsView.displayError('student-form-error', ''); // ID элемента ошибки

                const studentData = StudentsView.getFormData(false); // isEditMode = false

                if (!studentData.firstName || !studentData.lastName || !studentData.email) {
                     StudentsView.displayError('student-form-error', 'Имя, фамилия и email обязательны.');
                     return;
                }
                if (!studentData.password || studentData.password.length < 5) {
                     StudentsView.displayError('student-form-error', 'Пароль обязателен и должен быть не менее 5 символов.');
                     return;
                }

                try {
                    const createdStudent = await ApiService.createStudent(studentData);
                    alert(`Регистрация успешна для ${createdStudent.email}! Теперь вы можете войти.`);
                    AppRouter.navigateTo('/login');
                } catch (error) {
                    StudentsView.displayError('student-form-error', `Ошибка регистрации: ${error.message}`);
                }
            });

            const cancelButton = document.getElementById('cancel-student-form');
            if (cancelButton) {
                 cancelButton.addEventListener('click', () => AppRouter.navigateTo('/login'));
            } else {
                console.warn("Cancel button ('cancel-student-form') not found in registration form.");
            }
        } else {
            console.error("Student form ('student-form') not found after rendering for registration.");
            AppRouter.appContentElement.innerHTML = "<p class='error-message'>Ошибка: не удалось загрузить форму регистрации.</p>";
        }
    }
};