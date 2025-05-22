const CoursesView = {
    renderList: function(courses) {
        const isAdmin = Auth.isAdmin();
        let coursesHtml = '<table><thead><tr><th>ID</th><th>Название курса</th><th>Описание</th>';
        if (isAdmin) {
            coursesHtml += '<th>Действия</th>';
        }
        coursesHtml += '</tr></thead><tbody>';

        if (!courses || courses.length === 0) {
            coursesHtml += `<tr><td colspan="${isAdmin ? 4 : 3}">Список курсов пуст.</td></tr>`;
        } else {
            courses.forEach(course => {
                coursesHtml += `<tr>
                                 <td>${course.courseId}</td>
                                 <td>${course.courseName}</td>
                                 <td>${course.description || 'N/A'}</td>`;
                if (isAdmin) {
                    coursesHtml += `<td>
                                     <button class="edit-course" data-id="${course.courseId}">Ред.</button>
                                     <button class="delete-course danger" data-id="${course.courseId}">Уд.</button>
                                   </td>`;
                }
                coursesHtml += `</tr>`;
            });
        }
        coursesHtml += '</tbody></table>';

        let headerControls = '';
        if (isAdmin) {
            headerControls = '<button id="add-course-btn">Добавить новый курс</button>';
        }

        return `
            <h2>${isAdmin ? 'Управление Курсами' : 'Список доступных курсов'}</h2>
            <div id="courses-list-error" class="error-message"></div>
            ${headerControls}
            ${coursesHtml}
        `;
    },

    renderForm: function(course = {}) { // Только для админа
        if (!Auth.isAdmin()) {
            return `<p class="error-message">Доступ запрещен.</p>`;
        }
        const isEdit = course && course.courseId;
        return `
            <h2>${isEdit ? 'Редактировать курс' : 'Добавить новый курс'}</h2>
            <form id="course-form">
                <input type="hidden" id="courseId" value="${course.courseId || ''}">
                <div class="form-group">
                    <label for="courseName">Название курса:</label>
                    <input type="text" id="courseName" value="${course.courseName || ''}" required>
                </div>
                <div class="form-group">
                    <label for="description">Описание:</label>
                    <textarea id="description" rows="3">${course.description || ''}</textarea>
                </div>
                <p id="course-form-error" class="error-message"></p>
                <button type="submit">${isEdit ? 'Сохранить изменения' : 'Создать курс'}</button>
                <button type="button" id="cancel-course-form" class="secondary">Отмена</button>
            </form>
        `;
    },

    getFormData: function() {
        const course = {
            courseName: document.getElementById('courseName').value,
            description: document.getElementById('description').value,
        };
        const courseId = document.getElementById('courseId').value;
        if (courseId) {
            course.courseId = parseInt(courseId);
        }
        return course;
    },

    displayError: function(elementId, message) {
        const errorElement = document.getElementById(elementId);
        if(errorElement) errorElement.textContent = message;
    }
};