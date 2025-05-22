const GradesController = {
    // --- Студенческие функции ---
    myGrades: async function() {
        if (!Auth.isStudent()) {
            AppRouter.navigateTo(Auth.isAdmin() ? '/admin/dashboard' : '/login');
            return;
        }
        const userInfo = Auth.getUserInfo();
        if (!userInfo || !userInfo.userId) {
            Auth.logout();
            return;
        }
        try {
            AppRouter.appContentElement.innerHTML = '<p>Загрузка ваших оценок...</p>';
            const enrollments = await ApiService.getEnrollmentsForStudent(userInfo.userId);
            let allGradesWithDetails = [];

            if (enrollments && enrollments.length > 0) {
                for (const enroll of enrollments) {
                    const gradesForEnrollment = await ApiService.getGradesForEnrollment(enroll.studentCourseId);
                    if (gradesForEnrollment && gradesForEnrollment.length > 0) {
                        let courseName = `Курс ID ${enroll.courseId}`;
                        try {
                            const course = await ApiService.getCourse(enroll.courseId);
                            if (course) courseName = course.courseName;
                        } catch (e) {
                            console.warn(`Не удалось загрузить название курса ${enroll.courseId} для оценок студента.`);
                        }
                        gradesForEnrollment.forEach(grade => {
                            allGradesWithDetails.push({
                                grade: grade,
                                courseName: courseName,
                            });
                        });
                    }
                }
            }
            AppRouter.appContentElement.innerHTML = GradesView.renderMyGrades(allGradesWithDetails);
        } catch (error) {
            console.error("Error fetching my grades:", error);
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить ваши оценки: ${error.message}</p>`;
            GradesView.displayError('my-grades-error', `Не удалось загрузить ваши оценки: ${error.message}`);
        }
    },

    listMyGradesForEnrollment: async function(params) {
        if (!Auth.isStudent()) { AppRouter.navigateTo('/login'); return; }
        if (!params || !params.enrollmentId) {
            AppRouter.appContentElement.innerHTML = '<p class="error-message">ID записи на курс не указан.</p>';
            return;
        }
        const enrollmentId = parseInt(params.enrollmentId);
        const userInfo = Auth.getUserInfo();
        if (!userInfo) { Auth.logout(); return; }

        try {
            AppRouter.appContentElement.innerHTML = `<p>Загрузка оценок для записи ID ${enrollmentId}...</p>`;

            const enrollment = await ApiService.getEnrollmentById(enrollmentId);
            if (!enrollment || enrollment.studentId !== userInfo.userId) {
                throw new Error("Доступ к этим оценкам запрещен или запись не найдена.");
            }

            let courseName = `Курс ID ${enrollment.courseId}`;
            try {
                const course = await ApiService.getCourse(enrollment.courseId);
                if (course) courseName = course.courseName;
            } catch (e) { console.warn(`Не удалось загрузить название курса ${enrollment.courseId}`); }

            const grades = await ApiService.getGradesForEnrollment(enrollmentId);
            AppRouter.appContentElement.innerHTML = GradesView.renderMyGradesForEnrollment(grades, courseName, enrollmentId);
        } catch (error) {
            console.error("Error fetching my grades for enrollment:", error);
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить оценки: ${error.message}</p>`;
            GradesView.displayError('my-grades-enrollment-error', `Ошибка: ${error.message}`);
        }
    },

    // --- Админские функции ---
    showAddGradeFormForEnrollment: async function(params) { // params.enrollmentId
        if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
        if (!params || !params.enrollmentId) {
            AppRouter.appContentElement.innerHTML = '<p class="error-message">ID записи на курс не указан.</p>'; return;
        }
        const studentCourseId = parseInt(params.enrollmentId);
        try {
            AppRouter.appContentElement.innerHTML = '<p>Загрузка данных для добавления оценки...</p>';
            const enrollment = await ApiService.getEnrollmentById(studentCourseId);
            if (!enrollment) throw new Error(`Запись на курс с ID ${studentCourseId} не найдена.`);

            const student = await ApiService.getStudent(enrollment.studentId);
            const course = await ApiService.getCourse(enrollment.courseId);

            AppRouter.appContentElement.innerHTML = GradesView.renderGradeForm({}, studentCourseId, student, course);
            this.attachAdminFormEventListeners(false, studentCourseId, enrollment.studentId);
        } catch (error) {
            console.error("Error preparing add grade form (admin):", error);
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Ошибка подготовки формы оценки: ${error.message}</p>`;
            GradesView.displayError('grade-form-error', `Ошибка: ${error.message}`);
        }
    },

    showEditGradeForm: async function(params) { // params.gradeId
        if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
         if (!params || !params.gradeId) {
            AppRouter.appContentElement.innerHTML = '<p class="error-message">ID оценки не указан.</p>'; return;
        }
        const gradeId = parseInt(params.gradeId);
        try {
            AppRouter.appContentElement.innerHTML = '<p>Загрузка данных оценки...</p>';
            const grade = await ApiService.getGradeById(gradeId);
            if (!grade) throw new Error(`Оценка с ID ${gradeId} не найдена.`);

            const enrollment = await ApiService.getEnrollmentById(grade.studentCourseId);
            const student = enrollment ? await ApiService.getStudent(enrollment.studentId) : null;
            const course = enrollment ? await ApiService.getCourse(enrollment.courseId) : null;

            AppRouter.appContentElement.innerHTML = GradesView.renderGradeForm(grade, grade.studentCourseId, student, course);
            this.attachAdminFormEventListeners(true, grade.studentCourseId, enrollment ? enrollment.studentId : null);
        } catch (error) {
             console.error("Error loading grade for edit (admin):", error);
             AppRouter.appContentElement.innerHTML = `<p class="error-message">Ошибка загрузки оценки: ${error.message}</p>`;
             GradesView.displayError('grade-form-error', `Ошибка: ${error.message}`);
        }
    },

    attachAdminFormEventListeners: function(isEditMode, studentCourseIdContext, studentIdForBack = null) {
        const form = document.getElementById('grade-form');
        if (form) {
            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                GradesView.displayError('grade-form-error', '');
                const gradeData = GradesView.getFormData();

                if (!gradeData.studentCourseId) {
                     GradesView.displayError('grade-form-error', `Ошибка: ID записи на курс (studentCourseId) не определен.`);
                     return;
                }
                if (!gradeData.gradeValue) {
                     GradesView.displayError('grade-form-error', `Оценка не может быть пустой.`);
                     return;
                }
                if (!gradeData.gradeDate) {
                     GradesView.displayError('grade-form-error', `Дата оценки обязательна.`);
                     return;
                }

                try {
                    let message = '';
                    if (isEditMode) {
                        if (!gradeData.gradeId) {
                             GradesView.displayError('grade-form-error', `Ошибка: ID оценки не определен для редактирования.`);
                             return;
                        }
                        await ApiService.updateGrade(gradeData.gradeId, gradeData);
                        message = 'Оценка успешно обновлена!';
                    } else {
                        await ApiService.addGrade(gradeData);
                        message = 'Оценка успешно добавлена!';
                    }
                    alert(message);

                    // Редирект обратно к списку оценок для этой записи или студента
                    if (studentIdForBack) { // Если мы пришли со страницы оценок студента
                         AppRouter.navigateTo(`/admin/students/${studentIdForBack}/grades`);
                    } else { // Если мы пришли со страницы оценок для конкретной записи
                         AppRouter.navigateTo(`/admin/enrollments/${studentCourseIdContext}/grades`);
                    }
                } catch (error) {
                    console.error("Error saving grade (admin):", error);
                    GradesView.displayError('grade-form-error', `Ошибка сохранения оценки: ${error.message}`);
                }
            });

            const cancelButton = document.getElementById('cancel-grade-form');
            if(cancelButton){
                cancelButton.addEventListener('click', () => {
                    if (studentIdForBack) {
                        AppRouter.navigateTo(`/admin/students/${studentIdForBack}/grades`);
                    } else {
                        AppRouter.navigateTo(`/admin/enrollments/${studentCourseIdContext}/grades`);
                    }
                });
            }
        }
    },

    listStudentGradesAdmin: async function(params) {
        if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
        if (!params || !params.studentId) {
            AppRouter.appContentElement.innerHTML = '<p class="error-message">ID студента не указан.</p>'; return;
        }
        const studentId = parseInt(params.studentId);
        try {
            AppRouter.appContentElement.innerHTML = `<p>Загрузка данных студента ID ${studentId}...</p>`;
            const student = await ApiService.getStudent(studentId);
            if (!student) throw new Error("Студент не найден.");

            AppRouter.appContentElement.innerHTML = `<p>Загрузка оценок студента ${student.firstName} ${student.lastName}...</p>`;
            const enrollments = await ApiService.getEnrollmentsForStudent(studentId);
            let allGradesWithDetails = [];
            let hasEnrollments = enrollments && enrollments.length > 0;

            if (hasEnrollments) {
                for (const enroll of enrollments) {
                    const gradesForEnrollment = await ApiService.getGradesForEnrollment(enroll.studentCourseId);
                    const course = await ApiService.getCourse(enroll.courseId);

                    if (gradesForEnrollment && gradesForEnrollment.length > 0) {
                        gradesForEnrollment.forEach(grade => {
                            allGradesWithDetails.push({
                                grade: grade,
                                studentId: studentId,
                                studentName: `${student.firstName} ${student.lastName}`,
                                courseId: enroll.courseId,
                                courseName: course ? course.courseName : `Курс ID ${enroll.courseId}`,
                            });
                        });
                    } else { // Если оценок по этой записи нет, создаем "заглушку" для отображения курса
                         allGradesWithDetails.push({
                            grade: { studentCourseId: enroll.studentCourseId, gradeValue: '(нет оценки)', gradeDate: null, comments: null, gradeId: null },
                            studentId: studentId,
                            studentName: `${student.firstName} ${student.lastName}`,
                            courseId: enroll.courseId,
                            courseName: course ? course.courseName : `Курс ID ${enroll.courseId}`,
                        });
                    }
                }
            }

            let title = `Оценки студента: ${student.firstName} ${student.lastName}`;
            let renderContext = { type: 'student', id: studentId, noEnrollments: !hasEnrollments };
            AppRouter.appContentElement.innerHTML = GradesView.renderAdminGradeList(allGradesWithDetails, title, renderContext);
            // Добавляем кнопку для общего управления записями студента, откуда можно добавлять оценки к курсам без оценок
            AppRouter.appContentElement.innerHTML += `<hr/><button onclick="AppRouter.navigateTo('/admin/students/${studentId}/enrollments')">Управление записями студента (для добавления оценок)</button>`;
            this.attachAdminListEventListeners(studentId, null); // studentId для контекста обновления
        } catch (error) {
            console.error("Error listing student grades (admin):", error);
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить оценки студента: ${error.message}</p>`;
            GradesView.displayError('admin-grades-list-error', `Не удалось загрузить оценки: ${error.message}`);
        }
    },

    attachAdminListEventListeners: function(studentIdForContext = null, enrollmentIdForContext = null) {
        document.querySelectorAll('.edit-grade.admin-action').forEach(button => {
            button.addEventListener('click', (e) => {
                const gradeId = e.target.dataset.gradeId;
                const enrollmentId = e.target.dataset.enrollmentId;
                if (gradeId && gradeId !== "null") { // Редактируем существующую
                    AppRouter.navigateTo(`/admin/grades/edit/${gradeId}`);
                } else if (enrollmentId) { // Добавляем новую к этой записи
                    AppRouter.navigateTo(`/admin/enrollments/${enrollmentId}/grades/new`);
                }
            });
        });
        document.querySelectorAll('.delete-grade.admin-action').forEach(button => {
            button.addEventListener('click', async (e) => {
                const gradeId = e.target.dataset.gradeId;
                if (!gradeId || gradeId === "null") {
                    alert("Нельзя удалить несуществующую оценку.");
                    return;
                }
                if (confirm(`Вы уверены, что хотите удалить оценку с ID ${gradeId}?`)) {
                    try {
                        await ApiService.deleteGrade(gradeId);
                        // Обновляем текущий вид
                        if (studentIdForContext) {
                            this.listStudentGradesAdmin({studentId: studentIdForContext.toString()});
                        } else if (enrollmentIdForContext) {
                            this.listGradesForEnrollmentAdmin({enrollmentId: enrollmentIdForContext.toString()});
                        } else {
                            // Попытка общего обновления, если контекст не ясен (менее надежно)
                            if (window.location.hash.includes('/admin/students/')) {
                                const studentIdFromHash = window.location.hash.split('/admin/students/')[1].split('/')[0];
                                this.listStudentGradesAdmin({studentId: studentIdFromHash});
                            } else if (window.location.hash.includes('/admin/enrollments/')) {
                                const enrollmentIdFromHash = window.location.hash.split('/admin/enrollments/')[1].split('/')[0];
                                this.listGradesForEnrollmentAdmin({enrollmentId: enrollmentIdFromHash});
                            } else {
                                AppRouter.navigateTo('/admin/dashboard');
                            }
                        }
                    } catch (error) {
                        console.error("Error deleting grade (admin):", error);
                        alert(`Ошибка удаления оценки: ${error.message}`);
                        GradesView.displayError('admin-grades-list-error', `Ошибка удаления оценки: ${error.message}`);
                    }
                }
            });
        });
    },

    listGradesForEnrollmentAdmin: async function(params) {
        if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
        if (!params || !params.enrollmentId) {
            AppRouter.appContentElement.innerHTML = '<p class="error-message">ID записи на курс не указан.</p>'; return;
        }
        const studentCourseId = parseInt(params.enrollmentId);
        try {
            AppRouter.appContentElement.innerHTML = `<p>Загрузка данных для записи ID ${studentCourseId}...</p>`;
            const enrollment = await ApiService.getEnrollmentById(studentCourseId);
            if (!enrollment) throw new Error(`Запись на курс ID ${studentCourseId} не найдена.`);

            const student = await ApiService.getStudent(enrollment.studentId);
            const course = await ApiService.getCourse(enrollment.courseId);

            AppRouter.appContentElement.innerHTML = `<p>Загрузка оценок для ${student ? student.firstName : 'Студента'} по курсу ${course ? course.courseName : 'Курс'}...</p>`;
            const grades = await ApiService.getGradesForEnrollment(studentCourseId);

            const gradesWithDetails = grades.map(grade => ({
                grade: grade,
                studentName: student ? `${student.firstName} ${student.lastName}` : `Студент ID ${enrollment.studentId}`,
                studentId: enrollment.studentId,
                courseName: course ? course.courseName : `Курс ID ${enrollment.courseId}`,
                courseId: enrollment.courseId
            }));

            let title = `Оценки для записи ID ${studentCourseId}`;
            if (student && course) title = `Оценки для ${student.firstName} ${student.lastName} по курсу "${course.courseName}"`;

            let renderContext = { type: 'enrollment', id: studentCourseId, studentIdForBack: enrollment.studentId };
            AppRouter.appContentElement.innerHTML = GradesView.renderAdminGradeList(gradesWithDetails, title, renderContext);
            this.attachAdminListEventListeners(null, studentCourseId);

        } catch (error) {
            console.error("Error listing grades for enrollment (admin):", error);
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить оценки: ${error.message}</p>`;
            GradesView.displayError('admin-grades-list-error', `Не удалось загрузить оценки: ${error.message}`);
        }
    }
};