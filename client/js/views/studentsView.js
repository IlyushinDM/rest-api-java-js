const StudentsView = {
    renderList: function(students) {
        const isAdmin = Auth.isAdmin();
        let listTitle = "Список студентов";
        let headerControls = '';

        if (isAdmin) {
            listTitle = "Управление Студентами";
            headerControls = '<button id="add-student-btn" class="admin-action">Добавить нового студента</button>';
        } else if (Auth.isStudent()) {
            listTitle = "Информация о студентах";
        }

        let tableHtml = '<table>';
        tableHtml += '<thead><tr><th>ID</th><th>Имя</th><th>Фамилия</th><th>Email</th><th>Группа</th>';
        if (isAdmin) {
            tableHtml += '<th>Действия</th>';
        }
        tableHtml += '</tr></thead>';

        tableHtml += '<tbody>';
        if (!students || students.length === 0) {
            const colspan = isAdmin ? 6 : 5;
            tableHtml += `<tr><td colspan="${colspan}">Список студентов пуст.</td></tr>`;
        } else {
            students.forEach(student => {
                tableHtml += `<tr>
                                 <td>${student.studentId}</td>
                                 <td>${student.firstName}</td>
                                 <td>${student.lastName}</td>
                                 <td>${student.email}</td>
                                 <td>${student.groupName || 'N/A'}</td>`;
                if (isAdmin) {
                    tableHtml += `
                        <td class="actions-cell">
                            <button class="view-student-details admin-action" data-id="${student.studentId}" title="Просмотр деталей студента">Детали</button>
                            <button class="edit-student admin-action" data-id="${student.studentId}" title="Редактировать студента">Ред.</button>
                            <button class="delete-student danger admin-action" data-id="${student.studentId}" title="Удалить студента">Уд.</button>
                            <hr class="actions-separator">
                            <button class="manage-student-enrollments admin-action" data-id="${student.studentId}" title="Управление записями студента на курсы">Записи</button>
                            <button class="manage-student-grades admin-action" data-id="${student.studentId}" title="Управление оценками студента">Оценки</button>
                            <button class="manage-student-documents admin-action" data-id="${student.studentId}" title="Управление документами студента">Документы</button>
                        </td>`;
                }
                tableHtml += `</tr>`;
            });
        }
        tableHtml += '</tbody></table>';

        return `
            <div class="view-header">
                <h2>${listTitle}</h2>
                ${headerControls}
            </div>
            <div id="students-list-error" class="error-message"></div>
            ${tableHtml}
        `;
    },

    renderForm: function(student = {}, forRegistration = false) {
        if (!forRegistration && !Auth.isAdmin()) {
            return `<p class="error-message">Доступ к этой форме запрещен.</p>`;
        }

        const isEditByAdmin = !forRegistration && Auth.isAdmin() && student && student.studentId;
        const isCreateByAdmin = !forRegistration && Auth.isAdmin() && !(student && student.studentId);

        let title = '';
        if (forRegistration) {
            title = 'Регистрация нового студента';
        } else if (isEditByAdmin) {
            title = `Редактировать профиль студента (ID: ${student.studentId})`;
        } else if (isCreateByAdmin) {
            title = 'Добавить нового студента (Админ)';
        } else {
            return `<p class="error-message">Ошибка отображения формы: неверный контекст.</p>`;
        }

        const emailReadonly = isEditByAdmin; // Email нельзя менять после создания (даже админу)
        const groupEditableByAdmin = Auth.isAdmin(); // Группу может менять только админ

        return `
            <div class="form-container">
                <h2>${title}</h2>
                <form id="student-form">
                    <input type="hidden" id="studentId" name="studentId" value="${student.studentId || ''}">
                    <div class="form-group">
                        <label for="firstName">Имя:</label>
                        <input type="text" id="firstName" name="firstName" value="${student.firstName || ''}" required>
                    </div>
                    <div class="form-group">
                        <label for="lastName">Фамилия:</label>
                        <input type="text" id="lastName" name="lastName" value="${student.lastName || ''}" required>
                    </div>
                    <div class="form-group">
                        <label for="email">Email:</label>
                        <input type="email" id="email" name="email" value="${student.email || ''}" ${emailReadonly ? 'readonly' : 'required'}>
                    </div>
                    <div class="form-group">
                        <label for="groupName">Группа:</label>
                        <input type="text" id="groupName" name="groupName" value="${student.groupName || ''}" ${!groupEditableByAdmin && isEditByAdmin ? 'readonly' : ''}>
                    </div>
                    ${ (forRegistration || isCreateByAdmin) ? `
                    <div class="form-group">
                        <label for="password">Пароль:</label>
                        <input type="password" id="password" name="password" required minlength="5">
                    </div>` : ''}
                    ${isEditByAdmin ? `
                    <div class="form-group">
                        <label for="newPassword">Новый пароль (оставить пустым, если не менять):</label>
                        <input type="password" id="newPassword" name="newPassword" minlength="5">
                    </div>
                    ` : ''}
                    <p id="student-form-error" class="error-message"></p>
                    <button type="submit">${isEditByAdmin ? 'Сохранить изменения' : (forRegistration ? 'Зарегистрироваться' : 'Создать студента')}</button>
                    <button type="button" id="cancel-student-form" class="secondary">Отмена</button>
                </form>
            </div>
        `;
    },

    renderDetail: function(student) {
        const currentUserInfo = Auth.getUserInfo();
        if (!student) return `<p class="error-message">Информация о студенте не найдена.</p>`;

        let canViewDetails = false;
        if (Auth.isAdmin()) {
            canViewDetails = true;
        } else if (Auth.isStudent() && currentUserInfo && currentUserInfo.userId === student.studentId) {
            canViewDetails = true;
        }

        if (!canViewDetails) {
             return `<p class="error-message">Доступ к профилю студента ID ${student.studentId} запрещен.</p>`;
        }

        let adminActions = '';
        let studentActions = ''; // Для студента, просматривающего свой профиль

        if(Auth.isAdmin() && currentUserInfo && currentUserInfo.userId !== student.studentId) { // Админ смотрит чужой профиль
            adminActions = `
                <div class="profile-actions admin-profile-actions">
                    <h3>Действия администратора:</h3>
                    <button class="admin-action" onclick="AppRouter.navigateTo('/admin/students/edit/${student.studentId}')">Редактировать профиль</button>
                    <button class="admin-action" onclick="AppRouter.navigateTo('/admin/students/${student.studentId}/enrollments')">Записи на курсы</button>
                    <button class="admin-action" onclick="AppRouter.navigateTo('/admin/students/${student.studentId}/grades')">Оценки</button>
                    <button class="admin-action" onclick="AppRouter.navigateTo('/admin/students/${student.studentId}/documents')">Документы</button>
                </div>
            `;
        } else if (Auth.isStudent() && currentUserInfo && currentUserInfo.userId === student.studentId) {
            // Студент смотрит свой профиль
            studentActions = `
                <div class="profile-actions student-profile-actions">
                    <h3>Мои действия:</h3>
                    <button class="student-action" onclick="AppRouter.navigateTo('/my-profile/edit')">Редактировать мой профиль</button>
                    <button class="student-action" onclick="AppRouter.navigateTo('/my-courses')">Мои курсы</button>
                    <button class="student-action" onclick="AppRouter.navigateTo('/my-grades')">Мои оценки</button>
                    <button class="student-action" onclick="AppRouter.navigateTo('/my-documents')">Мои документы</button>
                </div>
            `;
        }


        return `
            <div class="profile-view">
                <h2>Детали студента: ${student.firstName} ${student.lastName}</h2>
                <div class="profile-info">
                    <p><strong>ID:</strong> ${student.studentId}</p>
                    <p><strong>Email:</strong> ${student.email}</p>
                    <p><strong>Группа:</strong> ${student.groupName || 'N/A'}</p>
                </div>
                ${adminActions}
                ${studentActions}
                <hr>
                <button onclick="window.history.back()" class="secondary">Назад</button>
            </div>
        `;
    },

    getFormData: function(isEditMode, forRegistration = false) {
        const student = {
            firstName: document.getElementById('firstName')?.value.trim() || '',
            lastName: document.getElementById('lastName')?.value.trim() || '',
            email: document.getElementById('email')?.value.trim() || '', // Email не должен меняться при редактировании, но админ может его задать при создании
            groupName: document.getElementById('groupName')?.value.trim() || '',
        };
        const studentIdInput = document.getElementById('studentId');
        if (studentIdInput && studentIdInput.value) {
            student.studentId = parseInt(studentIdInput.value);
        }

        const passwordField = document.getElementById('password');
        const newPasswordField = document.getElementById('newPassword');

        // Определяем, является ли текущий пользователь админом
        const isAdminEditing = isEditMode && Auth.isAdmin();
        const isAdminCreating = !isEditMode && Auth.isAdmin() && !forRegistration;

        if (forRegistration || isAdminCreating) {
            if (passwordField && passwordField.value) {
                student.password = passwordField.value;
            }
        } else if (isAdminEditing) {
            if (newPasswordField && newPasswordField.value) {
                student.newPassword = newPasswordField.value;
            }
        }
        // Если студент редактирует свой профиль (isEditMode=true, !Auth.isAdmin(), !forRegistration),
        // то для смены пароля ему нужна будет отдельная логика/форма или поле newPassword на его форме редактирования.
        // В текущей student.newPassword будет собираться только если isAdminEditing.
        return student;
    },
    displayError: function(elementId, message) {
        const errorElement = document.getElementById(elementId);
        if(errorElement) {
            errorElement.textContent = message;
        } else {
            console.warn(`StudentsView.displayError: Element with ID '${elementId}' not found.`);
        }
    }
};