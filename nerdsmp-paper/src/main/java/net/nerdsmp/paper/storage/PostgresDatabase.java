package net.nerdsmp.paper.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

public final class PostgresDatabase implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    public PostgresDatabase(
        String jdbcUrl,
        String username,
        String password,
        int maximumPoolSize,
        Logger logger
    ) throws SQLException, IOException {
        Objects.requireNonNull(logger, "logger");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(Math.max(2, maximumPoolSize));
        config.setMinimumIdle(1);
        config.setPoolName("NerdSMP-Postgres");
        config.setAutoCommit(true);
        config.setConnectionTimeout(10_000L);
        config.setValidationTimeout(5_000L);
        config.setLeakDetectionThreshold(30_000L);
        config.addDataSourceProperty("ApplicationName", "NerdSMP");

        dataSource = new HikariDataSource(config);
        ThreadFactory factory = Thread.ofPlatform().name("nerdsmp-database-", 0L).factory();
        executor = Executors.newFixedThreadPool(Math.max(2, Math.min(maximumPoolSize, 8)), factory);
        migrate(logger);
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    public ExecutorService executor() {
        return executor;
    }

    private void migrate(Logger logger) throws SQLException, IOException {
        executeMigration("/db/migration/V001__foundation.sql");
        executeMigration("/db/migration/V002__progression.sql");
        executeMigration("/db/migration/V003__skill_talents.sql");
        executeMigration("/db/migration/V004__npc_reputation.sql");
        logger.info("NerdSMP PostgreSQL schema is ready at version 4.");
    }

    private void executeMigration(String resource) throws SQLException, IOException {
        String migration;
        try (InputStream input = PostgresDatabase.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing database migration " + resource);
            }
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(migration);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        dataSource.close();
    }
}
