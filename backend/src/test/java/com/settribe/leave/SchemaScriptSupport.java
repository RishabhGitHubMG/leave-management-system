package com.settribe.leave;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies sql/schema.sql to an H2 database running in MySQL mode.
 * The MySQL-only CREATE DATABASE and USE statements are skipped.
 */
final class SchemaScriptSupport {

    private SchemaScriptSupport() {
    }

    static Connection open(String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    static void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String ddl : tableStatements()) {
                statement.execute(ddl);
            }
        }
    }

    private static List<String> tableStatements() {
        String sql;
        try {
            sql = Files.readString(Path.of("..", "sql", "schema.sql"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read sql/schema.sql", e);
        }
        List<String> statements = new ArrayList<>();
        for (String ddl : sql.substring(sql.indexOf("CREATE TABLE")).split(";")) {
            if (!ddl.isBlank()) {
                statements.add(ddl);
            }
        }
        return statements;
    }
}
