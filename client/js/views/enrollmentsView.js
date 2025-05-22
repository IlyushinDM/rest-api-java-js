const EnrollmentsView = {
    // Для студента: список его курсов
    renderMyCourses: function(enrollmentsWithCourseDetails) {
        if (!Auth.isStudent()) return '<p class="info-message">Эта страница предназначена для студентов.</p>';
        let html = '<h2>Мои курсы</h2>';
        html += '<div id="my-courses-error" class="error-message"></div>'; // Для ошибок

        if (!enrollmentsWithCourseDetails || enrollmentsWithCourseDetails.length === 0) {
            html += '<p>Вы еще не записаны ни на один курс.</p>';
        } else {
            html += '<ul class="course-list">';
            enrollmentsWithCourseDetails.forEach(item => {
                html += `<li class="course-list-item">
                            <h3>${item.course.courseName}</h3>
                            <p><strong>ID курса:</strong> ${item.course.courseId}</p>
                            <p><strong>Описание:</strong> ${item.course.description || 'Нет описания'}</p>
                            <p><strong>Записан:</strong> ${new Date(item.enrollment.enrollmentDate).toLocaleDateString()}</p>
                            <button class="unenroll-course danger" data-enrollment-id="${item.enrollment.studentCourseId}">Отписаться</button>
                            <button onclick="AppRouter.navigateTo('/my-grades/enrollment/${item.enrollment.studentCourseId}')">Оценки по этому курсу</button>
                         </li>`;
            });
            html += '</ul>';
        }
        html += '<hr/><button id="enroll-new-course-btn">Запись на новый курс</button>';
        return html;
    },

    // Для админа или студента: форма записи на курс
    renderEnrollmentForm: function(availableCourses, studentToEnroll = null) {
        // studentToEnroll - объект студента {id, firstName, lastName}, если админ записывает конкретного.
        // Если null, и текущий юзер - студент, то студент записывает себя.
        const currentUser = Auth.getUserInfo();
        let targetStudentId = null;
        let targetStudentName = '';
        let formTitle = 'Запись на курс';
        let readOnlyStudentId = false;

        if (Auth.isAdmin()) {
            if (studentToEnroll) {
                targetStudentId = studentToEnroll.studentId;
                targetStudentName = `${studentToEnroll.firstName} ${studentToEnroll.lastName} (ID: ${studentToEnroll.studentId})`;
                formTitle = `Записать студента ${targetStudentName} на курс`;
                readOnlyStudentId = true; // Админ выбрал студента, ID не меняем
            } else {
                formTitle = 'Записать студента на курс (Админ)';
                // Поле ID студента будет редактируемым
            }
        } else if (Auth.isStudent()) {
            if (studentToEnroll && studentToEnroll.studentId !== currentUser.userId) {
                 return '<p class="error-message">Ошибка: Студент не может записывать другого студента.</p>';
            }
            targetStudentId = currentUser.userId;
            targetStudentName = currentUser.userEmail; // или запросить полное имя
            formTitle = `Запись на новый курс для ${targetStudentName}`;
            readOnlyStudentId = true;
        } else {
            return '<p class="error-message">Доступ запрещен.</p>';
        }

        let courseOptions = '<option value="">-- Выберите курс --</option>';
        if (availableCourses && availableCourses.length > 0) {
            availableCourses.forEach(course => {
                courseOptions += `<option value="${course.courseId}">${course.courseName} (ID: ${course.courseId})</option>`;
            });
        } else {
            courseOptions = '<option value="">Нет доступных курсов для записи</option>';
        }

        return `
            <h2>${formTitle}</h2>
            <form id="enrollment-form">
                <div class="form-group">
                    <label for="studentIdForEnroll">ID Студента:</label>
                    <input type="number" id="studentIdForEnroll" value="${targetStudentId || ''}" required ${readOnlyStudentId ? 'readonly' : ''}>
                    ${targetStudentName && readOnlyStudentId ? `<small>Студент: ${targetStudentName}</small>` : ''}
                </div>
                <div class="form-group">
                    <label for="courseIdToEnroll">Курс:</label>
                    <select id="courseIdToEnroll" required>
                        ${courseOptions}
                    </select>
                </div>
                <p id="enrollment-form-error" class="error-message"></p>
                <button type="submit">Запись</button>
                <button type="button" id="cancel-enrollment-form" class="secondary">Отмена</button>
            </form>
        `;
    },

    renderAdminStudentEnrollments: function(student, enrollmentsWithCourseDetails) {
        if (!Auth.isAdmin()) return '<p class="error-message">Доступ запрещен.</p>';

        let html = `<h2>Записи студента: ${student.firstName} ${student.lastName} (ID: ${student.studentId})</h2>`;
        html += `<div id="admin-student-enrollments-error" class="error-message"></div>`;
        html += `<button onclick="AppRouter.navigateTo('/admin/enrollments/student/${student.studentId}/new')">Записать этого студента на новый курс</button><hr/>`;

        if (!enrollmentsWithCourseDetails || enrollmentsWithCourseDetails.length === 0) {
            html += '<p>Студент не записан ни на один курс.</p>';
        } else {
            html += '<ul class="enrollment-list-admin">';
            enrollmentsWithCourseDetails.forEach(item => {
                html += `<li class="enrollment-list-item-admin">
                            <h3>${item.course.courseName} <small>(ID курса: ${item.course.courseId})</small></h3>
                            <p><strong>ID:</strong> ${item.enrollment.studentCourseId}</p>
                            <p><strong>Дата записи:</strong> ${new Date(item.enrollment.enrollmentDate).toLocaleDateString()}</p>
                            <div class="actions">
                                <button class="admin-unenroll-student danger" data-enrollment-id="${item.enrollment.studentCourseId}" data-student-id="${student.studentId}">Отписать от курса</button>
                                <button class="admin-view-grades-for-enrollment" data-enrollment-id="${item.enrollment.studentCourseId}">Оценки по этому курсу</button>
                                <button class="admin-add-grade-to-enrollment" data-enrollment-id="${item.enrollment.studentCourseId}">Добавить/Изменить оценку</button>
                            </div>
                         </li>`;
            });
            html += '</ul>';
        }
        html += `<hr/><button onclick="AppRouter.navigateTo('/admin/students/detail/${student.studentId}')">К профилю студента</button>`;
        return html;
    },

    renderAdminCourseEnrollments: function(course, enrollmentsWithStudentDetails) {
        if (!Auth.isAdmin()) return '<p class="error-message">Доступ запрещен.</p>';

        let html = `<h2>Студенты на курсе: "${course.courseName}" (ID: ${course.courseId})</h2>`;
        html += `<div id="admin-course-enrollments-error" class="error-message"></div>`;
        if (!enrollmentsWithStudentDetails || enrollmentsWithStudentDetails.length === 0) {
            html += '<p>На этот курс еще никто не записан.</p>';
        } else {
            html += '<ul class="enrollment-list-admin">';
            enrollmentsWithStudentDetails.forEach(item => {
                html += `<li class="enrollment-list-item-admin">
                            <h4>${item.student.firstName} ${item.student.lastName} <small>(ID студента: ${item.student.studentId})</small></h4>
                            <p><strong>ID:</strong> ${item.enrollment.studentCourseId}</p>
                            <p><strong>Дата записи:</strong> ${new Date(item.enrollment.enrollmentDate).toLocaleDateString()}</p>
                            <div class="actions">
                                <button class="admin-unenroll-from-course danger" data-enrollment-id="${item.enrollment.studentCourseId}" data-course-id="${course.courseId}">Отписать от курса</button>
                                <button class="admin-view-grades-for-enrollment" data-enrollment-id="${item.enrollment.studentCourseId}">Оценки по этому курсу</button>
                            </div>
                         </li>`;
            });
            html += '</ul>';
        }
        html += `<hr/><button onclick="AppRouter.navigateTo('/admin/courses')">К списку курсов</button>`;
        return html;
    },

    renderAdminEnrollmentList: function(enrollmentsWithDetails, title = "Все записи на курсы") {
        if (!Auth.isAdmin()) return '<p class="error-message">Доступ запрещен.</p>';
        let html = `<h2>${title}</h2>`;
        html += `<div id="admin-all-enrollments-error" class="error-message"></div>`;
        if (!enrollmentsWithDetails || enrollmentsWithDetails.length === 0) {
            html += '<p>Записей нет.</p>';
        } else {
            html += '<table><thead><tr><th>ID</th><th>Студент</th><th>Курс</th><th>Дата записи</th><th>Действия</th></tr></thead><tbody>';
            enrollmentsWithDetails.forEach(e => {
                html += `<tr>
                            <td>${e.enrollment.studentCourseId}</td>
                            <td>${e.studentName || `ID: ${e.enrollment.studentId}`} <button class="view-student-details-link" data-student-id="${e.enrollment.studentId}">(Детали)</button></td>
                            <td>${e.courseName || `ID: ${e.enrollment.courseId}`} <button class="view-course-details-link" data-course-id="${e.enrollment.courseId}"></button></td>
                            <td>${new Date(e.enrollment.enrollmentDate).toLocaleDateString()}</td>
                            <td>
                                <button class="admin-unenroll danger" data-enrollment-id="${e.enrollment.studentCourseId}">Отписать</button>
                                <button class="admin-view-grades-for-enrollment" data-enrollment-id="${e.enrollment.studentCourseId}">Оценки</button>
                            </td>
                         </tr>`;
            });
            html += '</tbody></table>';
        }
        return html;
    },
    displayError: function(elementId, message) {
        const errorElement = document.getElementById(elementId);
        if(errorElement) errorElement.textContent = message;
    }
};