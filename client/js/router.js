const AppRouter = {
    routes: {},
    appContentElement: null,

    init: function(appContentElementId) {
        this.appContentElement = document.getElementById(appContentElementId);
        if (!this.appContentElement) {
            console.error("Router init failed: App content element not found.");
            return;
        }
        // Слушаем изменения хеша
        window.addEventListener('hashchange', this.handleRouteChange.bind(this));
        // Загружаем начальный маршрут
        this.handleRouteChange();
    },

    addRoute: function(path, controllerAction) {
        // path должен быть вида '/path' (без #)
        this.routes[path] = controllerAction;
    },

    handleRouteChange: function() {
        const rawHash = window.location.hash;
        let path = rawHash.startsWith('#') ? rawHash.substring(1) : clientConfig.defaultRoute.substring(1);

        console.log("Routing to:", path);

        // Базовая проверка доступа к админским маршрутам
        if (path.startsWith('/admin') && !Auth.isAdmin()) {
            console.warn("Access denied to admin route for non-admin user. Redirecting.");
            this.navigateTo(Auth.isAuthenticated() ? '/dashboard' : '/login'); // или на страницу ошибки "доступ запрещен"
            return;
        }

        // Проверка аутентификации для непубличных маршрутов
        const publicRoutes = ['/login', '/register']; // Добавьте сюда другие публичные маршруты
        if (!Auth.isAuthenticated() && !publicRoutes.includes(path) && !path.startsWith('/public')) { // Пример для /public/*
            console.log("Not authenticated, redirecting to login for path:", path);
            this.navigateTo('/login');
            return;
        }

        // Простой поиск маршрута. Для параметров (/:id) потребуется более сложная логика.
        // Пока что сделаем точное совпадение.
        let action = this.routes[path];
        let params = null;

        if (!action) {
            for (const routePath in this.routes) {
                const routeParts = routePath.split('/');
                const pathParts = path.split('/');
                if (routeParts.length === pathParts.length) {
                    let match = true;
                    let tempParams = {};
                    for (let i = 0; i < routeParts.length; i++) {
                        if (routeParts[i].startsWith(':')) {
                            tempParams[routeParts[i].substring(1)] = pathParts[i];
                        } else if (routeParts[i] !== pathParts[i]) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        action = this.routes[routePath];
                        params = tempParams;
                        // Устанавливаем "активный" путь для подсветки в навигации, если он с параметрами
                        // Это упрощение, для сложных случаев нужен более умный механизм
                        path = routePath.substring(0, routePath.indexOf('/:')) || routePath;
                        break;
                    }
                }
            }
        }


        if (action) {
            try {
                this.appContentElement.innerHTML = '<p>Загрузка...</p>'; // Показываем загрузку перед рендерингом
                action(params);
            } catch (error) {
                console.error("Error in controller action for path", path, error);
                this.appContentElement.innerHTML = `<p class="error-message">Ошибка при загрузке страницы.</p>`;
            }
        } else {
            console.warn("No route found for path:", path);
            this.appContentElement.innerHTML = `<p>Страница не найдена (404 Client Side).</p>`;
        }
        this.updateActiveLink(path); // Передаем текущий обработанный путь
    },

    navigateTo: function(path) { // path должен быть вида '/path'
        window.location.hash = path;
    },

    updateActiveLink: function(currentRoutePath) { // Принимает текущий путь без параметров
        const navLinks = document.querySelectorAll('#main-nav a');
        // Если currentRoutePath не передан, пытаемся получить из хеша, но это может быть неточным для параметризованных роутов
        const activePath = currentRoutePath || (window.location.hash ? window.location.hash.substring(1) : clientConfig.defaultRoute.substring(1));

        navLinks.forEach(link => {
            const linkPath = link.getAttribute('href').substring(1); // Убираем #
            // Простое сравнение, или более сложная логика для параметризованных роутов
            if (linkPath === activePath || activePath.startsWith(linkPath + '/')) {
                link.classList.add('active');
            } else {
                link.classList.remove('active');
            }
        });
    }
};