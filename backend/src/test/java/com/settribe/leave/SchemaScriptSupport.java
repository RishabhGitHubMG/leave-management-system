package com.settribe.leave;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

final class SchemaScriptSupport {

    private SchemaScriptSupport() {
    }

    static Connection open(String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    static MigratedDatabase openMigrated(String databaseName) throws SQLException {
        String url = newUrl(databaseName);
        Connection connection = open(url);
        try {
            migrate(connection);
            return new MigratedDatabase(url, connection);
        } catch (RuntimeException error) {
            try {
                connection.close();
                shutdown(url);
            } catch (SQLException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    static String newUrl(String databaseName) {
        return "jdbc:h2:mem:" + databaseName + "_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    static void migrate(Connection connection) throws SQLException {
        Flyway.configure()
                .dataSource(new SingleConnectionDataSource(connection, true))
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    static void shutdown(String url) throws SQLException {
        try (Connection connection = open(url); var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    static final class MigratedDatabase implements AutoCloseable {
        private final String url;
        private final Connection connection;

        private MigratedDatabase(String url, Connection connection) {
            this.url = url;
            this.connection = connection;
        }

        Connection connection() {
            return connection;
        }

        @Override
        public void close() throws SQLException {
            try {
                connection.close();
            } finally {
                shutdown(url);
            }
        }
    }
}
