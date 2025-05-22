const CoursesController = {
    listCourses: async function() {
        // Список курсов видят все аутентифицированные пользователи
        if (!Auth.isAuthenticated()) { AppRouter.navigateTo('/login'); return; }
        try {
            AppRouter.appContentElement.innerHTML = '<p>Загрузка курсов...</p>';
            const courses = await ApiService.getCourses();
            AppRouter.appContentElement.innerHTML = CoursesView.renderList(courses);
            if (Auth.isAdmin()) { // Только админы могут добавлять, редактировать, удалять
                this.attachAdminListEventListeners();
            }
        } catch (error) {
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить список курсов: ${error.message}</p>`;
        }
    },

    showCourseForm: async function(params) { // Только для админа
        if (!Auth.isAdmin()) { AppRouter.navigateTo(Auth.isStudent() ? '/dashboard' : '/login'); return; }
        let courseData = {};
        if (params && params.id) {
            try {
                AppRouter.appContentElement.innerHTML = '<p>Загрузка данных курса...</p>';
                courseData = await ApiService.getCourse(params.id); // Предполагаем, что есть ApiService.getCourse(id)
            } catch (error) {
                AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить данные курса: ${error.message}</p>`;
                return;
            }
        }
        AppRouter.appContentElement.innerHTML = CoursesView.renderForm(courseData);
        this.attachAdminFormEventListeners(!!(params && params.id));
    },

    attachAdminListEventListeners: function() {
        document.getElementById('add-course-btn')?.addEventListener('click', () => AppRouter.navigateTo('/admin/courses/new'));

        document.querySelectorAll('.edit-course').forEach(button => {
            button.addEventListener('click', (e) => AppRouter.navigateTo(`/admin/courses/edit/${e.target.dataset.id}`));
        });
        document.querySelectorAll('.delete-course').forEach(button => {
            button.addEventListener('click', async (e) => {
                const courseId = e.target.dataset.id;
                if (confirm(`Вы уверены, что хотите удалить курс с ID ${courseId}? Это также удалит все записи студентов на этот курс и их оценки по нему!`)) {
                    try {
                        await ApiService.deleteCourse(courseId); // Предполагаем, что есть ApiService.deleteCourse(id)
                        this.listCourses(); // Обновить список
                    } catch (error) {
                        CoursesView.displayError('courses-list-error', `Ошибка удаления курса: ${error.message}`);
                    }
                }
            });
        });
    },

    attachAdminFormEventListeners: function(isEditMode) {
        const form = document.getElementById('course-form');
        if (form) {
            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                CoursesView.displayError('course-form-error', '');
                const courseData = CoursesView.getFormData();
                try {
                    if (isEditMode) {
                        await ApiService.updateCourse(courseData.courseId, courseData); // ApiService.updateCourse(id, data)
                    } else {
                        await ApiService.createCourse(courseData);
                    }
                    AppRouter.navigateTo(Auth.isAdmin() ? '/admin/courses' : '/courses');
                } catch (error) {
                     CoursesView.displayError('course-form-error', `Ошибка сохранения курса: ${error.message}`);
                }
            });
            document.getElementById('cancel-course-form')?.addEventListener('click', () => AppRouter.navigateTo(Auth.isAdmin() ? '/admin/courses' : '/courses'));
        }
    }
};