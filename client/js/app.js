document.addEventListener('DOMContentLoaded', () => {
    console.log("Client App Initializing...");

    if (typeof Auth !== 'undefined' && Auth.updateNav) {
        Auth.updateNav();
    } else {
        console.error("Auth object or Auth.updateNav is not available at initial load.");
    }

    // --- Маршруты для всех (публичные) ---
    if (typeof AuthController !== 'undefined') {
        AppRouter.addRoute('/login', AuthController.login);
        AppRouter.addRoute('/register', AuthController.register);
    } else {
        console.error("AuthController is not defined. Login/Register routes cannot be added.");
    }

    // --- Общие маршруты для аутентифицированных пользователей ---
    if (typeof CoursesController !== 'undefined') {
        AppRouter.addRoute('/courses', CoursesController.listCourses.bind(CoursesController));
    } else {
        console.error("CoursesController is not defined. '/courses' route cannot be added.");
    }

    // --- Маршруты для СТУДЕНТОВ ---
    AppRouter.addRoute('/dashboard', () => {
        if (!Auth.isAuthenticated()) { AppRouter.navigateTo('/login'); return; }
        if (Auth.isAdmin()) { AppRouter.navigateTo('/admin/dashboard'); return; }
        if (Auth.isStudent() && typeof DashboardView !== 'undefined') {
            AppRouter.appContentElement.innerHTML = DashboardView.render();
        } else {
            console.warn("Could not render student dashboard.");
            Auth.logout();
        }
    });

    AppRouter.addRoute('/my-profile', () => {
        if (!Auth.isStudent()) { AppRouter.navigateTo(Auth.isAdmin() ? '/admin/dashboard' : '/login'); return; }
        const userInfo = Auth.getUserInfo();
        if (userInfo && userInfo.userId && typeof StudentsController !== 'undefined') {
            StudentsController.showStudentDetail.bind(StudentsController)({ id: userInfo.userId.toString() });
        } else {
            console.error("User info or StudentsController missing for /my-profile");
            Auth.logout();
        }
    });
    // TODO: Маршрут для редактирования студентом своего профиля
    AppRouter.addRoute('/my-profile/edit', () => {
        if (!Auth.isStudent()) { AppRouter.navigateTo('/login'); return; }
        // Здесь должен быть вызов StudentsController.showMyProfileEditForm() или аналогичного
        AppRouter.appContentElement.innerHTML = "<h2>Редактирование моего профиля (TODO)</h2> <p>Эта форма позволит студенту изменить некоторые свои данные (например, имя, фамилию, возможно, пароль).</p>";
    });


    if (typeof EnrollmentsController !== 'undefined') {
        AppRouter.addRoute('/my-courses', EnrollmentsController.myCourses.bind(EnrollmentsController));
        AppRouter.addRoute('/my-courses/enroll', (params) => EnrollmentsController.showEnrollmentForm.bind(EnrollmentsController)(params || {}));
    } else {
        console.error("EnrollmentsController is not defined. Student course routes cannot be added.");
    }

    if (typeof GradesController !== 'undefined') {
        AppRouter.addRoute('/my-grades', GradesController.myGrades.bind(GradesController));
        AppRouter.addRoute('/my-grades/enrollment/:enrollmentId', (params) => {
            if (!Auth.isStudent()) { AppRouter.navigateTo('/login'); return; }
            GradesController.listMyGradesForEnrollment.bind(GradesController)(params);
        });
    } else {
        console.error("GradesController is not defined. Student grade routes cannot be added.");
    }

    if (typeof DocumentsController !== 'undefined') {
        AppRouter.addRoute('/my-documents', DocumentsController.myDocuments.bind(DocumentsController));
    } else {
        console.error("DocumentsController is not defined. '/my-documents' route cannot be added.");
    }


    // --- Маршруты для АДМИНИСТРАТОРОВ ---
    AppRouter.addRoute('/admin/dashboard', () => {
        if (!Auth.isAdmin()) { AppRouter.navigateTo(Auth.isStudent() ? '/dashboard' : '/login'); return; }
        AppRouter.appContentElement.innerHTML = `
            <div class="view-header"><h2>Панель Администратора</h2></div>
            <p>Добро пожаловать, Администратор! Выберите действие из меню или из списка ниже.</p>
            <ul class="admin-dashboard-links">
                <li><a href="#/admin/students">Управление студентами</a></li>
                <li><a href="#/admin/courses">Управление курсами</a></li>
                <li><a href="#/admin/enrollments">Все записи на курсы</a></li>
                <li><a href="#/admin/enrollments/new">Записать студента на курс (форма)</a></li>
            </ul>`;
    });

    // --- Админ: Управление Студентами ---
    if (typeof StudentsController !== 'undefined') {
        AppRouter.addRoute('/admin/students', StudentsController.listStudents.bind(StudentsController));
        AppRouter.addRoute('/admin/students/new', StudentsController.showStudentForm.bind(StudentsController));
        AppRouter.addRoute('/admin/students/edit/:id', StudentsController.showStudentForm.bind(StudentsController));
        AppRouter.addRoute('/admin/students/detail/:id', StudentsController.showStudentDetail.bind(StudentsController));
    } else {
        console.error("StudentsController is not defined. Admin student routes cannot be added.");
    }

    if (typeof EnrollmentsController !== 'undefined') {
        AppRouter.addRoute('/admin/students/:studentId/enrollments', (params) => {
            if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
            EnrollmentsController.listStudentEnrollmentsAdmin.bind(EnrollmentsController)(params);
        });
        AppRouter.addRoute('/admin/enrollments/student/:studentId/new', (params) => {
            if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
            EnrollmentsController.showEnrollmentForm.bind(EnrollmentsController)(params);
        });
    }

    if (typeof GradesController !== 'undefined') {
        AppRouter.addRoute('/admin/students/:studentId/grades', (params) => {
            if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
            GradesController.listStudentGradesAdmin.bind(GradesController)(params);
        });
    }

    if (typeof DocumentsController !== 'undefined') {
        AppRouter.addRoute('/admin/students/:studentId/documents', (params) => {
            if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
            DocumentsController.listStudentDocumentsAdmin.bind(DocumentsController)(params);
        });
    }


    // --- Админ: Управление Курсами ---
    if (typeof CoursesController !== 'undefined') {
        AppRouter.addRoute('/admin/courses', CoursesController.listCourses.bind(CoursesController));
        AppRouter.addRoute('/admin/courses/new', CoursesController.showCourseForm.bind(CoursesController));
        AppRouter.addRoute('/admin/courses/edit/:id', CoursesController.showCourseForm.bind(CoursesController));
    }

    if (typeof EnrollmentsController !== 'undefined') {
        AppRouter.addRoute('/admin/courses/:courseId/enrollments', (params) => {
            if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
            EnrollmentsController.listCourseEnrollmentsAdmin.bind(EnrollmentsController)(params);
        });
    }


    // --- Админ: Управление Записями на курсы (Enrollments) ---
    if (typeof EnrollmentsController !== 'undefined') {
        AppRouter.addRoute('/admin/enrollments', EnrollmentsController.listAllEnrollments.bind(EnrollmentsController));
        AppRouter.addRoute('/admin/enrollments/new', (params) => {
            if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
            EnrollmentsController.showEnrollmentForm.bind(EnrollmentsController)(params || {});
        });
    }


    // --- Админ: Управление Оценками ---
    if (typeof GradesController !== 'undefined') {
        AppRouter.addRoute('/admin/enrollments/:enrollmentId/grades', (params) => {
            if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
            GradesController.listGradesForEnrollmentAdmin.bind(GradesController)(params);
        });
        AppRouter.addRoute('/admin/enrollments/:enrollmentId/grades/new', (params) => {
            if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
            GradesController.showAddGradeFormForEnrollment.bind(GradesController)(params);
        });
        AppRouter.addRoute('/admin/grades/edit/:gradeId', (params) => {
            if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
            GradesController.showEditGradeForm.bind(GradesController)(params);
        });
    }


    // 3. Инициализация роутера
    if (typeof AppRouter !== 'undefined' && AppRouter.init) {
        AppRouter.init('app-content');
    } else {
        console.error("AppRouter or AppRouter.init is not available. Routing will not work.");
        const appContent = document.getElementById('app-content');
        if(appContent) {
            appContent.innerHTML =
            '<p class="error-message">Ошибка инициализации приложения: роутер не найден. Пожалуйста, проверьте консоль на наличие ошибок загрузки скриптов.</p>';
        }
    }

    // 4. Начальная навигация при загрузке страницы
    // (Этот блок остается таким же, как в предыдущей версии app.js)
    if (Auth.isAuthenticated()) {
        const userInfo = Auth.getUserInfo();
        const currentHashPath = window.location.hash.substring(1) || '/'; // Получаем путь без #
        const publicPaths = ['/login', '/register', '/']; // Учитываем и пустой хеш

        if (userInfo && publicPaths.includes(currentHashPath)) {
            if (userInfo.userType === 'EMPLOYEE' && userInfo.role === 'ADMIN') {
                AppRouter.navigateTo('/admin/dashboard');
            } else if (userInfo.userType === 'STUDENT') {
                AppRouter.navigateTo('/dashboard');
            }
        } else if (currentHashPath === '/' && clientConfig.defaultRoute && clientConfig.defaultRoute !== '#/') {
             AppRouter.navigateTo(clientConfig.defaultRoute.substring(1));
        }
    } else {
        const currentHashPath = window.location.hash.substring(1) || '/';
        const publicPathsForGuest = ['/login', '/register'];
        if (!publicPathsForGuest.includes(currentHashPath) && currentHashPath !== '/') {
            AppRouter.navigateTo('/login');
        } else if (currentHashPath === '/' && clientConfig.defaultRoute) {
            AppRouter.navigateTo(clientConfig.defaultRoute.substring(1));
        }
    }

    console.log("Client App Initialized and routes configured (if controllers were available).");
});