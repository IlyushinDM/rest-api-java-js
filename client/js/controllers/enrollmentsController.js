const EnrollmentsController = {
    myCourses: async function() {
        if (!Auth.isStudent()) { AppRouter.navigateTo('/login'); return; }
        const userInfo = Auth.getUserInfo();
        if (!userInfo) { Auth.logout(); return; } // На всякий случай
        try {
            AppRouter.appContentElement.innerHTML = '<p>Загрузка ваших курсов...</p>';
            const enrollments = await ApiService.getEnrollmentsForStudent(userInfo.userId);
            const enrollmentsWithDetails = await Promise.all(enrollments.map(async (enroll) => {
                try {
                    const course = await ApiService.getCourse(enroll.courseId);
                    return { enrollment: enroll, course: course || { courseId: enroll.courseId, courseName: `Курс ID ${enroll.courseId} (не найден)`} };
                } catch (e) {
                    console.error(`Ошибка загрузки курса ID ${enroll.courseId} для моих курсов:`, e);
                    return { enrollment: enroll, course: { courseId: enroll.courseId, courseName: `Курс ID ${enroll.courseId} (ошибка загрузки)`} };
                }
            }));
            AppRouter.appContentElement.innerHTML = EnrollmentsView.renderMyCourses(enrollmentsWithDetails);
            this.attachMyCoursesEventListeners();
        } catch (error) {
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить ваши курсы: ${error.message}</p>`;
            EnrollmentsView.displayError('my-courses-error', `Не удалось загрузить ваши курсы: ${error.message}`);
        }
    },

    showEnrollmentForm: async function(params = {}) { // params может содержать studentId
        if (!Auth.isAuthenticated()) { AppRouter.navigateTo('/login'); return; }

        let studentToEnroll = null;
        const studentIdFromParams = params.studentId ? parseInt(params.studentId) : null;
        const currentUser = Auth.getUserInfo();

        if (Auth.isAdmin() && studentIdFromParams) {
            try {
                studentToEnroll = await ApiService.getStudent(studentIdFromParams);
            } catch (e) {
                AppRouter.appContentElement.innerHTML = `<p class="error-message">Студент с ID ${studentIdFromParams} не найден.</p>`;
                return;
            }
        } else if (Auth.isStudent() && !studentIdFromParams) {
            studentToEnroll = { studentId: currentUser.userId, firstName: currentUser.userEmail, lastName: '(Вы)' }; // Упрощенно
        } else if (Auth.isStudent() && studentIdFromParams && studentIdFromParams !== currentUser.userId) {
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Вы не можете записывать других студентов.</p>`;
            return;
        } else if (!Auth.isAdmin()){
             AppRouter.appContentElement.innerHTML = `<p class="error-message">Доступ запрещен.</p>`;
            return;
        }

        try {
            AppRouter.appContentElement.innerHTML = '<p>Загрузка доступных курсов...</p>';
            const availableCourses = await ApiService.getCourses();
            AppRouter.appContentElement.innerHTML = EnrollmentsView.renderEnrollmentForm(availableCourses, studentToEnroll);
            this.attachEnrollmentFormListeners();
        } catch (error) {
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Ошибка загрузки формы: ${error.message}</p>`;
        }
    },

    attachMyCoursesEventListeners: function() {
        document.querySelectorAll('.unenroll-course').forEach(button => {
            button.addEventListener('click', async (e) => {
                const enrollmentId = e.target.dataset.enrollmentId;
                if (confirm('Вы уверены, что хотите отписаться от этого курса?')) {
                    try {
                        await ApiService.unenrollStudent(enrollmentId);
                        this.myCourses();
                    } catch (error) {
                        EnrollmentsView.displayError('my-courses-error', `Ошибка отписки: ${error.message}`);
                    }
                }
            });
        });
        document.getElementById('enroll-new-course-btn')?.addEventListener('click', () => {
            AppRouter.navigateTo('/my-courses/enroll');
        });
        document.querySelectorAll('.view-my-grades-for-course').forEach(button => {
            button.addEventListener('click', (e) => {
                const enrollmentId = e.target.dataset.enrollmentId;
                const courseName = e.target.dataset.courseName;
                // TODO: Перейти на страницу оценок студента по этому курсу/записи
                AppRouter.navigateTo(`/my-grades/enrollment/${enrollmentId}`);
                // или 
                // GradesController.listMyGradesForEnrollment({enrollmentId, courseName})
                // alert(`Переход к оценкам по курсу "${courseName}" (ID записи ${enrollmentId}) - TODO`);
            });
        });
    },

    attachEnrollmentFormListeners: function() {
        const form = document.getElementById('enrollment-form');
        if (form) {
            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                EnrollmentsView.displayError('enrollment-form-error', '');
                const studentIdInput = document.getElementById('studentIdForEnroll');
                const courseIdInput = document.getElementById('courseIdToEnroll');

                if (!studentIdInput || !courseIdInput) {
                     EnrollmentsView.displayError('enrollment-form-error', 'Ошибка формы: не найдены поля ID.');
                    return;
                }

                const studentId = parseInt(studentIdInput.value);
                const courseId = parseInt(courseIdInput.value);

                if (isNaN(studentId) || studentId <= 0) {
                    EnrollmentsView.displayError('enrollment-form-error', 'ID студента указан неверно.');
                    return;
                }
                if (isNaN(courseId) || courseId <= 0) {
                    EnrollmentsView.displayError('enrollment-form-error', 'Пожалуйста, выберите курс.');
                    return;
                }

                try {
                    await ApiService.enrollStudent({ studentId, courseId });
                    alert('Студент успешно записан на курс!');
                    if (Auth.isAdmin()) {
                        AppRouter.navigateTo(`/admin/students/${studentId}/enrollments`);
                    } else {
                        AppRouter.navigateTo('/my-courses');
                    }
                } catch (error) {
                    EnrollmentsView.displayError('enrollment-form-error', `Ошибка записи: ${error.message}`);
                }
            });
            document.getElementById('cancel-enrollment-form')?.addEventListener('click', () => {
                 const studentIdInput = document.getElementById('studentIdForEnroll');
                 const studentId = studentIdInput ? studentIdInput.value : null;
                 if (Auth.isAdmin()) {
                    if (studentId) AppRouter.navigateTo(`/admin/students/${studentId}/enrollments`);
                    else AppRouter.navigateTo('/admin/enrollments');
                 } else {
                    AppRouter.navigateTo('/my-courses');
                 }
            });
        }
    },

    // --- Админские функции ---
    // 1. EnrollmentsController.listStudentEnrollmentsAdmin(params)
    listStudentEnrollmentsAdmin: async function(params) {
        if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
        if (!params || !params.studentId) {
            AppRouter.appContentElement.innerHTML = '<p class="error-message">ID студента не указан.</p>'; return;
        }
        const studentId = parseInt(params.studentId);
        try {
            AppRouter.appContentElement.innerHTML = `<p>Загрузка записей студента ID ${studentId}...</p>`;
            const student = await ApiService.getStudent(studentId);
            if (!student) throw new Error("Студент не найден.");

            const enrollments = await ApiService.getEnrollmentsForStudent(studentId);
            const enrollmentsWithCourseDetails = await Promise.all(enrollments.map(async (enroll) => {
                try {
                    const course = await ApiService.getCourse(enroll.courseId);
                    return { enrollment: enroll, course: course || { courseId: enroll.courseId, courseName: `Курс ID ${enroll.courseId} (не найден)` } };
                } catch (e) {
                    return { enrollment: enroll, course: { courseId: enroll.courseId, courseName: `Курс ID ${enroll.courseId} (ошибка)` } };
                }
            }));

            AppRouter.appContentElement.innerHTML = EnrollmentsView.renderAdminStudentEnrollments(student, enrollmentsWithCourseDetails);
            this.attachAdminStudentEnrollmentsListeners(studentId);
        } catch (error) {
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить записи студента: ${error.message}</p>`;
            EnrollmentsView.displayError('admin-student-enrollments-error',`Не удалось загрузить записи студента: ${error.message}`);
        }
    },

    attachAdminStudentEnrollmentsListeners: function(studentIdForContext) {
        document.querySelectorAll('.admin-unenroll-student').forEach(button => {
            button.addEventListener('click', async (e) => {
                const enrollmentId = e.target.dataset.enrollmentId;
                if (confirm('Вы уверены, что хотите отписать этого студента от курса?')) {
                    try {
                        await ApiService.unenrollStudent(enrollmentId);
                        this.listStudentEnrollmentsAdmin({ studentId: studentIdForContext.toString() });
                    } catch (error) {
                        EnrollmentsView.displayError('admin-student-enrollments-error', `Ошибка отписки: ${error.message}`);
                    }
                }
            });
        });
        document.querySelectorAll('.admin-view-grades-for-enrollment').forEach(button => {
            button.addEventListener('click', (e) => {
                const enrollmentId = e.target.dataset.enrollmentId;
                AppRouter.navigateTo(`/admin/enrollments/${enrollmentId}/grades`);
            });
        });
        document.querySelectorAll('.admin-add-grade-to-enrollment').forEach(button => {
            button.addEventListener('click', (e) => {
                const enrollmentId = e.target.dataset.enrollmentId;
                AppRouter.navigateTo(`/admin/enrollments/${enrollmentId}/grades/new`);
            });
        });
    },

    // 3. EnrollmentsController.listCourseEnrollmentsAdmin(params)
    listCourseEnrollmentsAdmin: async function(params) {
        if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
        if (!params || !params.courseId) {
            AppRouter.appContentElement.innerHTML = '<p class="error-message">ID курса не указан.</p>'; return;
        }
        const courseId = parseInt(params.courseId);
        try {
            AppRouter.appContentElement.innerHTML = `<p>Загрузка студентов на курсе ID ${courseId}...</p>`;
            const course = await ApiService.getCourse(courseId);
            if (!course) throw new Error("Курс не найден.");

            const enrollments = await ApiService.getEnrollmentsForCourse(courseId);
            const enrollmentsWithStudentDetails = await Promise.all(enrollments.map(async (enroll) => {
                try {
                    const student = await ApiService.getStudent(enroll.studentId);
                    return { enrollment: enroll, student: student || { studentId: enroll.studentId, firstName: `Студент ID ${enroll.studentId}`, lastName:'' } };
                } catch (e) {
                     return { enrollment: enroll, student: { studentId: enroll.studentId, firstName: `Студент ID ${enroll.studentId} (ошибка)`, lastName:'' } };
                }
            }));

            AppRouter.appContentElement.innerHTML = EnrollmentsView.renderAdminCourseEnrollments(course, enrollmentsWithStudentDetails);
            this.attachAdminCourseEnrollmentsListeners(courseId);
        } catch (error) {
             AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить студентов курса: ${error.message}</p>`;
             EnrollmentsView.displayError('admin-course-enrollments-error',`Не удалось загрузить студентов курса: ${error.message}`);
        }
    },

    attachAdminCourseEnrollmentsListeners: function(courseIdForContext) {
        document.querySelectorAll('.admin-unenroll-from-course').forEach(button => {
            button.addEventListener('click', async (e) => {
                const enrollmentId = e.target.dataset.enrollmentId;
                 if (confirm('Вы уверены, что хотите отписать этого студента от курса?')) {
                    try {
                        await ApiService.unenrollStudent(enrollmentId);
                        this.listCourseEnrollmentsAdmin({ courseId: courseIdForContext.toString() });
                    } catch (error) {
                        EnrollmentsView.displayError('admin-course-enrollments-error', `Ошибка отписки: ${error.message}`);
                    }
                }
            });
        });
         document.querySelectorAll('.admin-view-grades-for-enrollment').forEach(button => {
            button.addEventListener('click', (e) => {
                const enrollmentId = e.target.dataset.enrollmentId;
                AppRouter.navigateTo(`/admin/enrollments/${enrollmentId}/grades`);
            });
        });
    },

    listAllEnrollments: async function() {
        if (!Auth.isAdmin()) { AppRouter.navigateTo('/login'); return; }
        try {
            AppRouter.appContentElement.innerHTML = '<p>Загрузка всех записей на курсы...</p>';
            const enrollments = await ApiService.getAllEnrollments();

            const enrollmentsWithDetails = await Promise.all(enrollments.map(async (enroll) => {
                let studentName = `Студент ID ${enroll.studentId}`;
                let courseName = `Курс ID ${enroll.courseId}`;
                try {
                    const student = await ApiService.getStudent(enroll.studentId);
                    if(student) studentName = `${student.firstName} ${student.lastName}`;
                } catch(e){/* ignore */}
                try {
                    const course = await ApiService.getCourse(enroll.courseId);
                    if(course) courseName = course.courseName;
                } catch(e){/* ignore */}
                return { enrollment: enroll, studentName, courseName };
            }));

            AppRouter.appContentElement.innerHTML = EnrollmentsView.renderAdminEnrollmentList(enrollmentsWithDetails, "Все записи на курсы");
            this.attachAdminGeneralEnrollmentListListeners();
        } catch (error) {
            AppRouter.appContentElement.innerHTML = `<p class="error-message">Не удалось загрузить все записи: ${error.message}</p>`;
            EnrollmentsView.displayError('admin-all-enrollments-error', `Не удалось загрузить все записи: ${error.message}`);
        }
    },
    attachAdminGeneralEnrollmentListListeners: function() {
        document.querySelectorAll('.admin-unenroll').forEach(button => {
            button.addEventListener('click', async (e) => {
                const enrollmentId = e.target.dataset.enrollmentId;
                if (confirm('Вы уверены, что хотите удалить эту запись?')) {
                    try {
                        await ApiService.unenrollStudent(enrollmentId);
                        this.listAllEnrollments();
                    } catch (error) {
                        EnrollmentsView.displayError('admin-all-enrollments-error', `Ошибка удаления записи: ${error.message}`);
                    }
                }
            });
        });
         document.querySelectorAll('.admin-view-grades-for-enrollment').forEach(button => {
            button.addEventListener('click', (e) => {
                const enrollmentId = e.target.dataset.enrollmentId;
                AppRouter.navigateTo(`/admin/enrollments/${enrollmentId}/grades`);
            });
        });
        document.querySelectorAll('.view-student-details-link').forEach(button => {
            button.addEventListener('click', e => AppRouter.navigateTo(`/admin/students/detail/${e.target.dataset.studentId}`));
        });
        // document.querySelectorAll('.view-course-details-link').forEach(button => {
        //     button.addEventListener('click', e => {
        //         // Пока нет страницы деталей курса для админа, можно направить на список курсов или страницу редактирования
        //         alert(`Детали курса ID ${e.target.dataset.courseId} (TODO)`);
        //         // AppRouter.navigateTo(`/admin/courses/edit/${e.target.dataset.courseId}`);
        //     });
        // });
    }
};