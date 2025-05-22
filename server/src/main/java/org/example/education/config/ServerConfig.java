package org.example.education.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ServerConfig {
    private static final Logger logger = LoggerFactory.getLogger(ServerConfig.class);
    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE = "server.properties";

    static {
        // Статический блок инициализации: загружает конфигурацию сервера из файла server.properties.
        try (InputStream input = ServerConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                logger.error("Sorry, unable to find " + CONFIG_FILE);
                throw new RuntimeException("Configuration file " + CONFIG_FILE + " not found in classpath.");
            }
            properties.load(input);
            logger.info("Server configuration loaded successfully from {}", CONFIG_FILE);
        } catch (IOException ex) {
            logger.error("Error loading server configuration", ex);
            throw new RuntimeException("Error loading configuration file " + CONFIG_FILE, ex);
        }
    }

    public static int getServerPort() {
        // Возвращает порт сервера из файла конфигурации, по умолчанию 8080.
        return Integer.parseInt(properties.getProperty("server.port", "8080"));
    }

    public static String getServerIp() {
        // Возвращает IP-адрес сервера из файла конфигурации, по умолчанию "0.0.0.0".
        return properties.getProperty("server.ip", "0.0.0.0");
    }

    public static String getDbUrl() {
        // Возвращает URL базы данных из файла конфигурации.
        return properties.getProperty("db.url");
    }

    public static String getDbUsername() {
        // Возвращает имя пользователя базы данных из файла конфигурации.
        return properties.getProperty("db.username");
    }

    public static String getDbPassword() {
        // Возвращает пароль базы данных из файла конфигурации.
        return properties.getProperty("db.password");
    }

    public static int getDbPoolSize() {
        // Возвращает размер пула соединений базы данных из файла конфигурации, по умолчанию 5.
        return Integer.parseInt(properties.getProperty("db.pool.size", "5"));
    }

    public static String getJwtSecretKey() {
        // Возвращает секретный ключ JWT из файла конфигурации.
        // Если ключ не задан или слишком короткий, возвращает небезопасный ключ по умолчанию и предупреждает в лог.
        String key = properties.getProperty("jwt.secretKey");
        if (key == null || key.length() < 32) { // 32 байта = 256 бит, минимум для HS256
            logger.warn("JWT secret key is not set or too short in server.properties. Using a default insecure key. PLEASE CONFIGURE A STRONG KEY!");
            return "DefaultInsecureSecretKeyPleaseChangeImmediatelyAndMakeItLong";
        }
        return key;
    }

    public static String getJwtIssuer() {
        // Возвращает издателя JWT из файла конфигурации, по умолчанию "org.example.education.api".
        return properties.getProperty("jwt.issuer", "org.example.education.api");
    }

    public static long getJwtExpirationMillis() {
        // Возвращает время жизни JWT в миллисекундах из файла конфигурации, по умолчанию 60 минут.
        return Long.parseLong(properties.getProperty("jwt.expiration.minutes", "60")) * 60 * 1000L;
    }
}