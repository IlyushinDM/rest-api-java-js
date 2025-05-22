package org.example.education.dao;

import org.example.education.config.ServerConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static HikariDataSource dataSource;

    static {
        try {
            Class.forName("org.postgresql.Driver");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(ServerConfig.getDbUrl());
            config.setUsername(ServerConfig.getDbUsername());
            config.setPassword(ServerConfig.getDbPassword());
            config.setMaximumPoolSize(ServerConfig.getDbPoolSize());
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            // Другие настройки HikariCP по необходимости

            dataSource = new HikariDataSource(config);
            logger.info("Database connection pool initialized successfully.");

        } catch (ClassNotFoundException e) {
            logger.error("PostgreSQL JDBC Driver not found!", e);
            throw new RuntimeException("PostgreSQL JDBC Driver not found!", e);
        } catch (Exception e) {
            logger.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Failed to initialize database connection pool", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }
}