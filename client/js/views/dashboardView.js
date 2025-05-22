const DashboardView = {
    render: function() {
        const userInfo = Auth.getUserInfo();
        return `
            <h2>Панель управления</h2>
            <p>Добро пожаловать, ${userInfo ? userInfo.userEmail : 'Пользователь'}!</p>
            <p>Это главная страница вашего личного кабинета.</p>
            <!-- Здесь может быть дополнительная информация, статистика и т.д. -->
        `;
    }
};