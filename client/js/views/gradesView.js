const GradesView = {
    // --- Студенческие представления ---

    /**
     * Рендерит список всех оценок для текущего студента.
     * @param {Array<Object>} gradesWithDetails - Массив объектов {grade: Grade, courseName: string}
     */
    renderMyGrades: function(gradesWithDetails) {
        if (!Auth.isStudent()) {
            return '<p class="info-message">Эта страница предназначена только для студентов.</p>';
        }
        let html = '<h2>Мои оценки</h2>';
        html += '<div id="my-grades-error" class="error-message"></div>';

        if (!gradesWithDetails || gradesWithDetails.length === 0) {
            html += '<p>У вас пока нет оценок. Записывайтесь на курсы и получайте знания!</p>';
        } else {
            html += '<table class="grades-table">';
            html += '<thead><tr><th>Курс</th><th>Оценка</th><th>Дата выставления</th><th>Комментарий</th></tr></thead>';
            html += '<tbody>';
            gradesWithDetails.forEach(item => {
                html += `<tr>
                            <td>${item.courseName || 'Курс не указан'}</td>
                            <td>${item.grade.gradeValue || 'N/A'}</td>
                            <td>${item.grade.gradeDate ? new Date(item.grade.gradeDate).toLocaleDateString() : 'N/A'}</td>
                            <td>${item.grade.comments || ''}</td>
                         </tr>`;
            });
            html += '</tbody></table>';
        }
        return html;
    },

    /**
     * Рендерит оценки студента по конкретной записи на курс.
     * @param {Array<Object>} grades - Массив объектов Grade.
     * @param {string} courseName - Название курса.
     * @param {number} enrollmentId - ID записи на курс.
     */
    renderMyGradesForEnrollment: function(grades, courseName, enrollmentId) {
        if (!Auth.isStudent()) {
            return '<p class="info-message">Доступ запрещен.</p>';
        }

        let html = `<h2>Мои оценки по курсу: "${courseName || 'Неизвестный курс'}"</h2>`;
        html += `<p><small>(Запись ID: ${enrollmentId})</small></p>`;
        html += '<div id="my-grades-enrollment-error" class="error-message"></div>';

        if (!grades || grades.length === 0) {
            html += `<p>По этой записи на курс (${courseName || 'Неизвестный курс'}) у вас пока нет оценок.</p>`;
        } else {
            html += '<table class="grades-table">';
            html += '<thead><tr><th>Оценка</th><th>Дата выставления</th><th>Комментарий</th></tr></thead>';
            html += '<tbody>';
            grades.forEach(grade => {
                html += `<tr>
                            <td>${grade.gradeValue || 'N/A'}</td>
                            <td>${grade.gradeDate ? new Date(grade.gradeDate).toLocaleDateString() : 'N/A'}</td>
                            <td>${grade.comments || ''}</td>
                         </tr>`;
            });
            html += '</tbody></table>';
        }
        html += `<hr/><button onclick="AppRouter.navigateTo('/my-courses')" class="secondary">Назад к моим курсам</button>`;
        return html;
    },

    // --- Представления для Администратора ---

    /**
     * Рендерит форму для добавления или редактирования оценки администратором.
     * @param {Object} grade - Существующая оценка для редактирования (пустой объект для новой).
     * @param {number|null} studentCourseIdContext - ID записи на курс, если добавляем новую оценку.
     * @param {Object|null} studentContext - Объект студента {firstName, lastName, studentId}.
     * @param {Object|null} courseContext - Объект курса {courseName, courseId}.
     */
    renderGradeForm: function(grade = {}, studentCourseIdContext = null, studentContext = null, courseContext = null) {
        if (!Auth.isAdmin()) { // TODO: Расширить для роли Преподавателя, если будет
            return `<p class="error-message">Доступ к этой форме запрещен.</p>`;
        }
        const isEdit = grade && grade.gradeId;
        const targetStudentCourseId = isEdit ? grade.studentCourseId : studentCourseIdContext;

        let contextInfo = '';
        if (studentContext && courseContext) {
            contextInfo = `<div class="form-context-info">
                            <p><strong>Студент:</strong> ${studentContext.firstName} ${studentContext.lastName} (ID: ${studentContext.studentId})</p>
                            <p><strong>Курс:</strong> ${courseContext.courseName} (ID: ${courseContext.courseId})</p>
                            <p><strong>Запись (Enrollment ID):</strong> ${targetStudentCourseId}</p>
                           </div>`;
        } else if (targetStudentCourseId) {
             contextInfo = `<p class="form-context-info"><strong>Запись (Enrollment ID):</strong> ${targetStudentCourseId}
                            <em>(Детали студента/курса не были загружены для отображения здесь)</em></p>`;
        }

        return `
            <div class="form-container">
                <h2>${isEdit ? 'Редактировать оценку' : 'Добавить оценку'}</h2>
                ${contextInfo}
                <form id="grade-form">
                    <input type="hidden" id="gradeId" value="${grade.gradeId || ''}">
                    <input type="hidden" id="studentCourseIdForGrade" value="${targetStudentCourseId || ''}">

                    <div class="form-group">
                        <label for="gradeValue">Оценка (например, A, 5, Зачет):</label>
                        <input type="text" id="gradeValue" name="gradeValue" value="${grade.gradeValue || ''}" required>
                    </div>
                    <div class="form-group">
                        <label for="gradeDate">Дата выставления (гггг-мм-дд):</label>
                        <input type="date" id="gradeDate" name="gradeDate" value="${grade.gradeDate || new Date().toISOString().split('T')[0]}" required>
                    </div>
                    <div class="form-group">
                        <label for="gradeComments">Комментарий (необязательно):</label>
                        <textarea id="gradeComments" name="gradeComments" rows="3">${grade.comments || ''}</textarea>
                    </div>
                    <p id="grade-form-error" class="error-message"></p>
                    <button type="submit">${isEdit ? 'Сохранить изменения' : 'Добавить оценку'}</button>
                    <button type="button" id="cancel-grade-form" class="secondary">Отмена</button>
                </form>
            </div>
        `;
    },

    /**
     * Рендерит список оценок для администратора (например, для студента или для записи на курс).
     * @param {Array<Object>} gradesWithDetails - Массив объектов {grade, studentName, studentId, courseName, courseId}
     * @param {string} title - Заголовок для списка.
     * @param {Object} context - Контекстная информация { type: 'student' | 'enrollment', id: studentId | enrollmentId, studentIdForBack?: number }
     */
    renderAdminGradeList: function(gradesWithDetails, title, context) {
         if (!Auth.isAdmin()) return '<p class="error-message">Доступ запрещен.</p>';

        let html = `<div class="view-header"><h2>${title}</h2>`;
        // Кнопка "Добавить оценку" имеет смысл только если мы в контексте одной записи на курс
        if (context && context.type === 'enrollment') {
            html += `<button class="admin-add-grade-to-this-enrollment" data-enrollment-id="${context.id}">Добавить оценку к этой записи</button>`;
        }
        html += `</div>`;
        html += `<div id="admin-grades-list-error" class="error-message"></div>`;

         if (!gradesWithDetails || gradesWithDetails.length === 0) {
            html += '<p>Оценок по указанным критериям нет.</p>';
            if (context && context.type === 'student' && context.noEnrollments) { // Если у студента нет записей на курсы
                 html += `<p>Студент не записан на курсы, для которых можно было бы выставить оценки.</p>
                          <button onclick="AppRouter.navigateTo('/admin/students/${context.id}/enrollments')">Управление записями этого студента</button>`;
            }
        } else {
            html += '<table class="grades-table-admin">'; // Отдельный класс для возможной стилизации
            html += '<thead><tr><th>ID Оценки</th><th>Студент</th><th>Курс</th><th>Оценка</th><th>Дата</th><th>Комментарий</th><th>Действия</th></tr></thead>';
            html += '<tbody>';
            gradesWithDetails.forEach(item => {
                // Формируем информацию о студенте и курсе
                let studentInfo = item.studentName ? `${item.studentName} (ID: ${item.studentId})` : (item.studentId ? `Студент ID ${item.studentId}` : 'N/A');
                let courseInfo = item.courseName ? `${item.courseName} (ID: ${item.courseId})` : (item.courseId ? `Курс ID ${item.courseId}` : 'N/A');

                let actionsHtml = '';
                // item.grade.studentCourseId - это ID записи, к которой привязана оценка (или куда ее добавлять)
                if (item.grade && item.grade.gradeId && item.grade.gradeId !== null) { // Если есть реальная оценка
                    actionsHtml = `
                        <button class="edit-grade admin-action" data-grade-id="${item.grade.gradeId}" title="Редактировать оценку">Ред.</button>
                        <button class="delete-grade danger admin-action" data-grade-id="${item.grade.gradeId}" title="Удалить оценку">Уд.</button>
                    `;
                } else if (item.grade && item.grade.studentCourseId) { // Если это заглушка "нет оценки" для курса
                    actionsHtml = `<button class="edit-grade admin-action" data-enrollment-id="${item.grade.studentCourseId}" title="Добавить первую оценку">Добавить оценку</button>`;
                } else {
                    actionsHtml = '<span>N/A</span>'; // Если нет ни ID оценки, ни ID записи
                }

                html += `<tr>
                            <td>${item.grade.gradeId || 'N/A'}</td>
                            <td>${studentInfo}</td>
                            <td>${courseInfo}</td>
                            <td>${item.grade.gradeValue || '(нет оценки)'}</td>
                            <td>${item.grade.gradeDate ? new Date(item.grade.gradeDate).toLocaleDateString() : 'N/A'}</td>
                            <td>${item.grade.comments || ''}</td>
                            <td class="actions-cell">${actionsHtml}</td>
                         </tr>`;
            });
            html += '</tbody></table>';
        }

        // Кнопки навигации "Назад"
        html += '<hr class="separator"/>';
        if (context) {
            if (context.type === 'student' && context.id) {
                html += `<button onclick="AppRouter.navigateTo('/admin/students/detail/${context.id}')" class="secondary">К профилю студента</button> `;
                html += `<button onclick="AppRouter.navigateTo('/admin/students/${context.id}/enrollments')" class="secondary">К записям студента</button>`;
            } else if (context.type === 'enrollment' && context.id) {
                if (context.studentIdForBack) { // Если мы знаем ID студента для этой записи
                     html += `<button onclick="AppRouter.navigateTo('/admin/students/${context.studentIdForBack}/enrollments')" class="secondary">К записям студента</button>`;
                } else { // Общий возврат, если студент не известен из контекста
                    html += `<button onclick="AppRouter.navigateTo('/admin/enrollments')" class="secondary">Ко всем записям</button>`;
                }
            }
        }
        html += ` <button onclick="AppRouter.navigateTo('/admin/dashboard')" class="secondary">На админ-панель</button>`;

        return html;
    },

    // --- Общие вспомогательные методы ---
    getFormData: function() {
        const studentCourseIdInput = document.getElementById('studentCourseIdForGrade');
        const gradeIdInput = document.getElementById('gradeId');
        const gradeValueInput = document.getElementById('gradeValue');
        const gradeDateInput = document.getElementById('gradeDate');
        const gradeCommentsInput = document.getElementById('gradeComments');

        // Проверка на существование элементов перед доступом к .value
        const gradeData = {
            studentCourseId: studentCourseIdInput ? parseInt(studentCourseIdInput.value) : null,
            gradeValue: gradeValueInput ? gradeValueInput.value.trim() : '',
            gradeDate: gradeDateInput ? gradeDateInput.value : '',
            comments: gradeCommentsInput ? gradeCommentsInput.value.trim() : '',
        };

        if (gradeIdInput && gradeIdInput.value) {
            gradeData.gradeId = parseInt(gradeIdInput.value);
        }
        return gradeData;
    },

    displayError: function(elementId, message) {
        const errorElement = document.getElementById(elementId);
        if(errorElement) {
            errorElement.textContent = message;
        } else {
            console.warn(`GradesView.displayError: Element with ID '${elementId}' not found.`);
            // Можно добавить вывод ошибки в какой-то общий блок, если специфичный не найден
            // const generalErrorArea = document.getElementById('general-app-error');
            // if (generalErrorArea) generalErrorArea.textContent = message;
        }
    }
};