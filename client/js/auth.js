const Auth = {
    _tokenKey: 'authToken',
    _userInfoKey: 'userInfo', // Теперь здесь будет { userId, userEmail, userType, role }

    login: async function(email, password) {
        try {
            const data = await ApiService.login({ email, password });
            // Сервер теперь возвращает userType и role в объекте Session
            if (data.token && data.userId && data.userEmail && data.userType && data.role) {
                localStorage.setItem(this._tokenKey, data.token);
                localStorage.setItem(this._userInfoKey, JSON.stringify({
                    userId: data.userId,
                    userEmail: data.userEmail,
                    userType: data.userType, // STUDENT или EMPLOYEE
                    role: data.role          // ADMIN или STUDENT
                }));
                this.updateNav();
                // Перенаправляем на дашборд, который может быть разным для разных ролей
                if (data.userType === 'EMPLOYEE' && data.role === 'ADMIN') {
                    AppRouter.navigateTo('/admin/dashboard'); // Пример админского дашборда
                } else {
                    AppRouter.navigateTo('/dashboard'); // Дашборд студента
                }
                return true;
            }
            // Если сервер не вернул userType или role, считаем логин неудачным
            throw new Error(data.error || 'Ответ сервера не содержит необходимой информации о пользователе.');
        } catch (error) {
            console.error('Login failed:', error.message);
            if (document.getElementById('login-error')) {
                 document.getElementById('login-error').textContent = `Ошибка входа: ${error.message || 'Неверные учетные данные'}`;
            }
            return false;
        }
    },

    logout: function() {
        ApiService.logout();
        localStorage.removeItem(this._tokenKey);
        localStorage.removeItem(this._userInfoKey);
        this.updateNav();
        AppRouter.navigateTo('/login');
    },

    getToken: function() {
        return localStorage.getItem(this._tokenKey);
    },

    getUserInfo: function() {
        const userInfo = localStorage.getItem(this._userInfoKey);
        try {
            return userInfo ? JSON.parse(userInfo) : null;
        } catch (e) {
            console.error("Error parsing user info from localStorage", e);
            localStorage.removeItem(this._userInfoKey); // Очищаем некорректные данные
            return null;
        }
    },

    isAuthenticated: function() {
        return !!this.getToken();
    },

    isStudent: function() {
        const userInfo = this.getUserInfo();
        return userInfo && userInfo.userType === 'STUDENT';
    },

    isAdmin: function() {
        const userInfo = this.getUserInfo();
        return userInfo && userInfo.userType === 'EMPLOYEE' && userInfo.role === 'ADMIN';
    },
    // Можно добавить isTeacher, если такая роль появится

    updateNav: function() {
        const nav = document.getElementById('main-nav');
        if (!nav) return;

        const userInfo = this.getUserInfo();
        let navHtml = '<ul>';

        if (this.isAuthenticated() && userInfo) {
            // navHtml += `<li><span>Привет, ${userInfo.userEmail} (${userInfo.role})!</span></li>`;
            if (this.isAdmin()) {
                // Навигация для Администратора
                navHtml += `<li><a href="#/admin/dashboard">Админ Панель</a></li>`;
                navHtml += `<li><a href="#/admin/students">Упр. Студентами</a></li>`;
                navHtml += `<li><a href="#/admin/courses">Упр. Курсами</a></li>`;
                navHtml += `<li><a href="#/admin/enrollments">Упр. Записями</a></li>`;
                // ... другие админские ссылки
            } else if (this.isStudent()) {
                // Навигация для Студента
                navHtml += `<li><a href="#/dashboard">Моя Панель</a></li>`;
                navHtml += `<li><a href="#/my-profile">Мой Профиль</a></li>`;
                navHtml += `<li><a href="#/my-courses">Мои Курсы</a></li>`;
                navHtml += `<li><a href="#/my-grades">Мои Оценки</a></li>`;
                navHtml += `<li><a href="#/my-documents">Мои Документы</a></li>`;
            }
            navHtml += `<li><a href="#" id="logout-link">Выйти</a></li>`;
        } else {
            // Навигация для неаутентифицированных пользователей
            navHtml += `<li><a href="#/login">Вход</a></li>`;
            navHtml += `<li><a href="#/register">Регистрация</a></li>`;
        }
        navHtml += '</ul>';
        nav.innerHTML = navHtml;

        if (this.isAuthenticated()) {
            document.getElementById('logout-link')?.addEventListener('click', (e) => {
                e.preventDefault();
                this.logout();
            });
        }
        AppRouter.updateActiveLink();
    }
};