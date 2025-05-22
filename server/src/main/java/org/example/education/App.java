package org.example.education;

import org.example.education.config.ServerConfig;
import org.example.education.controller.*;
import org.example.education.dao.*;
import org.example.education.filter.AuthenticationFilter;
import org.example.education.service.*;
import org.example.education.util.JsonUtil;
import org.example.education.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Scanner;

import static spark.Spark.*;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");

    public static void main(String[] args) {
        // --- ВРЕМЕННЫЙ КОД ДЛЯ ГЕНЕРАЦИИ ХЕША ---
//        String adminPasswordToHash = "adminpass";
//        String generatedHash = org.example.education.util.PasswordUtil.hashPassword(adminPasswordToHash);
//        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//        System.out.println("DEBUG: BCrypt HASH for '" + adminPasswordToHash + "' IS: " + generatedHash);
//        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        // --- КОНЕЦ ВРЕМЕННОГО КОДА ---

        logger.info("Starting Educational Institution Server...");

        // 1. Загрузка конфигурации
        logger.info("Server port from config: {}", ServerConfig.getServerPort());
        logger.info("JWT Issuer: {}", ServerConfig.getJwtIssuer());
        logger.info("Database URL: {}", ServerConfig.getDbUrl());

        // 2. Инициализация менеджера БД
        try {
            DatabaseManager.getConnection().close();
            logger.info("Database connection pool initialized successfully.");
        } catch (Exception e) {
            logger.error("Failed to connect to database or initialize pool. Exiting.", e);
            System.exit(1);
        }

        // 3. Настройка SparkJava
        port(ServerConfig.getServerPort());
        ipAddress(ServerConfig.getServerIp());

        // 4. Инициализация JwtUtil
        JwtUtil jwtUtil = new JwtUtil();

        // 5. Настройка CORS
        // Передаем сюда строки из server.properties или используем '*'
        String allowedOrigin = "*"; // Или ServerConfig.getAllowedOrigin() если вынесли в конфиг
        String allowedMethods = "GET, POST, PUT, DELETE, OPTIONS";
        String allowedHeaders = "Authorization, Content-Type, Accept, X-Requested-With";
        enableCORS(allowedOrigin, allowedMethods, allowedHeaders);

        // 6. Глобальные фильтры (до и после)
        before((request, response) -> {
            // Записывает время начала обработки запроса в атрибут.
            long startTime = System.currentTimeMillis();
            request.attribute("startTime", startTime);
        });

        // Фильтр аутентификации - он будет применен ПОСЛЕ enableCORS.
        // Для OPTIONS запросов он теперь будет делать return в самом начале.
        before("/api/*", new AuthenticationFilter(jwtUtil));

        // After-фильтр для логирования ответа
        after((request, response) -> {
            // Логирует информацию о запросе и ответе, включая время обработки, статус, URI, и данные пользователя.
            long startTime = 0L;
            Object startTimeAttr = request.attribute("startTime");
            if (startTimeAttr instanceof Long) {
                startTime = (Long) startTimeAttr;
            }
            long duration = System.currentTimeMillis() - startTime;

            auditLogger.info("RES {} {} {} {}ms (User: {}, Email: {}, IP: {})",
                    request.requestMethod(),
                    request.uri(),
                    response.status(),
                    duration,
                    request.attribute("userId"),
                    request.attribute("userEmail"),
                    request.ip()
            );
            response.header("X-Response-Time", String.valueOf(duration));
        });


        // 7. Инициализация DAO
        StudentDao studentDao = new StudentDao();
        EmployeeDao employeeDao = new EmployeeDao();
        CourseDao courseDao = new CourseDao();
        EnrollmentDao enrollmentDao = new EnrollmentDao();
        GradeDao gradeDao = new GradeDao();
        DocumentDao documentDao = new DocumentDao();

        // 8. Инициализация сервисов
        AuthService authService = new AuthService(studentDao, employeeDao, jwtUtil);
        StudentService studentService = new StudentService(studentDao);
        CourseService courseService = new CourseService(courseDao);
        EnrollmentService enrollmentService = new EnrollmentService(enrollmentDao, studentDao, courseDao);
        GradeService gradeService = new GradeService(gradeDao, enrollmentDao);
        DocumentService documentService = new DocumentService(documentDao, studentDao);

        // 9. Инициализация контроллеров
        new AuthController(authService);
        new StudentController(studentService);
        new CourseController(courseService);
        new EnrollmentController(enrollmentService);
        new GradeController(gradeService, enrollmentService);
        new DocumentController(documentService);


        // 10. Обработчики исключений
        exception(Exception.class, (exception, request, response) -> {
            // Обрабатывает необработанные исключения, логирует их и возвращает клиенту сообщение об ошибке.
            logger.error("Unhandled exception for request {} {}: ", request.requestMethod(), request.pathInfo(), exception);
            response.status(500);
            response.type("application/json");
            response.body(JsonUtil.toJson(Collections.singletonMap("error", "Internal Server Error. Please contact support.")));
            auditLogger.error("Unhandled exception caught: {} {} by User: {}, IP: {}. Exception: {}",
                    request.requestMethod(), request.pathInfo(),
                    request.attribute("userEmail"), request.ip(),
                    exception.toString());
        });

        notFound((req, res) -> {
            // Обрабатывает запросы к несуществующим ресурсам, возвращает клиенту сообщение об ошибке 404.
            res.type("application/json");
            res.status(404);
            auditLogger.warn("Resource not found: {} {} from IP: {} by User: {}", req.requestMethod(), req.pathInfo(), req.ip(), req.attribute("userEmail"));
            return JsonUtil.toJson(Collections.singletonMap("error", "The requested resource was not found."));
        });

        logger.info("Server configuration complete. SparkJava is initializing routes...");
        Spark.awaitInitialization();
        logger.info("Server started successfully on http://{}:{}", ServerConfig.getServerIp(), ServerConfig.getServerPort());
        auditLogger.info("SERVER_STARTED on http://{}:{}", ServerConfig.getServerIp(), ServerConfig.getServerPort());


        // 11. Консольное управление
        startConsoleAdminThread();

        // 12. Очистка ресурсов при завершении работы
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Выполняется при завершении работы сервера, останавливает Spark, закрывает DataSource и логирует завершение.
            logger.info("Shutting down server...");
            auditLogger.info("SERVER_STOPPING...");
            Spark.stop();
            DatabaseManager.closeDataSource();
            logger.info("Server stopped successfully.");
            auditLogger.info("SERVER_STOPPED.");
        }, "ShutdownHookThread"));
    }

    private static void enableCORS(final String origin, final String methods, final String headers) {
        // Настраивает CORS для указанных origin, methods и headers.
        options("/*", (request, response) -> {
            auditLogger.info("Handling OPTIONS pre-flight request for path: {}", request.pathInfo());

            response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Allow-Methods", methods);

            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null && !accessControlRequestHeaders.isEmpty()) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
                auditLogger.debug("Responding with Access-Control-Allow-Headers: {}", accessControlRequestHeaders);
            } else {
                response.header("Access-Control-Allow-Headers", headers); // Все разрешенные по умолчанию
                auditLogger.debug("No Access-Control-Request-Headers from client, responding with default: {}", headers);
            }

            response.status(204);
            return "";
        });

        before((request, response) -> {
            // Устанавливает заголовок Access-Control-Allow-Origin и тип контента для каждого запроса.
            if (!request.requestMethod().equalsIgnoreCase("OPTIONS")) {
                response.header("Access-Control-Allow-Origin", origin);
            }
            response.type("application/json"); // Устанавливаем тип контента по умолчанию для всех ответов
        });
    }

    private static void startConsoleAdminThread() {
        // Запускает консольный интерфейс администратора для управления сервером.
        Thread consoleThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            logger.info("Console admin interface started. Type 'help' for commands or 'exit' to stop.");
            try {
                while (true) {
                    System.out.print("admin> ");
                    if (!scanner.hasNextLine()) { // Обработка EOF
                        logger.info("Console input stream closed (EOF). Exiting console admin thread.");
                        break;
                    }
                    String commandLine = scanner.nextLine();
                    String command = commandLine.trim().toLowerCase();
                    if (command.isEmpty()) {
                        continue;
                    }

                    switch (command) {
                        case "stop":
                        case "exit":
                            logger.info("Stop command received from console. Initiating server shutdown...");
                            System.exit(0);
                            return;
                        case "status":
                            System.out.println("---- Server Status ----");
                            System.out.println("  State: Running");
                            System.out.println("  Listening on: " + ServerConfig.getServerIp() + ":" + ServerConfig.getServerPort());
                            try { // Добавим try-catch для ManagementFactory, на всякий случай
                                System.out.println("  Active Threads: " + Thread.activeCount());
                                System.out.println("  Uptime: " + (
                                        System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) / 1000 + " seconds");
                            } catch (Exception e) {
                                System.out.println("  Could not retrieve some metrics: " + e.getMessage());
                            }
                            System.out.println("-----------------------");
                            break;
                        case "help":
                            System.out.println("Available commands:");
                            System.out.println("  status - Show server status and basic metrics.");
                            System.out.println("  stop   - Stop the server gracefully.");
                            System.out.println("  exit   - Alias for stop.");
                            break;
                        default:
                            System.out.println("Unknown command: '" + command + "'. Type 'help' for available commands.");
                            break;
                    }
                }
            } catch (IllegalStateException e) {
                logger.warn("Scanner closed or System.in not available. Console admin thread stopping: " + e.getMessage());
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }
        }, "ConsoleAdminThread");
        consoleThread.setDaemon(true);
        consoleThread.start();
    }
}