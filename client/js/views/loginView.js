const LoginView = {
    render: function() {
        return `
            <div id="login-form-container">
                <h2>Вход в систему</h2>
                <form id="login-form">
                    <div class="form-group">
                        <label for="email">Email:</label>
                        <input type="email" id="email" name="email" required>
                    </div>
                    <div class="form-group">
                        <label for="password">Пароль:</label>
                        <input type="password" id="password" name="password" required>
                    </div>
                    <p id="login-error" class="error-message"></p>
                    <button type="submit">Войти</button>
                </form>
                <p>Нет аккаунта? <a href="#/register">Зарегистрироваться</a></p>
            </div>
        `;
    },
    // Можно добавить методы для получения данных из формы и т.д.
    getCredentials: function() {
        const email = document.getElementById('email').value;
        const password = document.getElementById('password').value;
        return { email, password };
    },
    displayError: function(message) {
        const errorElement = document.getElementById('login-error');
        if (errorElement) {
            errorElement.textContent = message;
        }
    }
};