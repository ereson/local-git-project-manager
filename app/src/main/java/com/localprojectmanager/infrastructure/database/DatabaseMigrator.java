package com.localprojectmanager.infrastructure.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

final class DatabaseMigrator {

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "initial schema", "/database/migrations/V001__initial_schema.sql")
    );

    private DatabaseMigrator() {
    }

    static void migrate(Connection connection) throws IOException, SQLException {
        createVersionTable(connection);
        var installedVersions = installedVersions(connection);
        for (var migration : MIGRATIONS) {
            if (!installedVersions.contains(migration.version())) {
                apply(connection, migration);
            }
        }
    }

    private static void createVersionTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version      INTEGER PRIMARY KEY,
                        description  TEXT NOT NULL,
                        installed_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private static HashSet<Integer> installedVersions(Connection connection) throws SQLException {
        var versions = new HashSet<Integer>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT version FROM schema_version")) {
            while (result.next()) {
                versions.add(result.getInt(1));
            }
        }
        return versions;
    }

    private static void apply(Connection connection, Migration migration) throws IOException, SQLException {
        var previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            executeScript(connection, readResource(migration.resource()));
            try (var statement = connection.prepareStatement("""
                    INSERT INTO schema_version(version, description, installed_at)
                    VALUES (?, ?, ?)
                    """)) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.description());
                statement.setString(3, Instant.now().toString());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (IOException | SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static String readResource(String path) throws IOException {
        try (var stream = DatabaseMigrator.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing migration resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void executeScript(Connection connection, String sql) throws SQLException {
        // ponytail: DDL-only scripts; use a real SQL parser before adding triggers or semicolons in strings.
        for (var statementSql : sql.split(";")) {
            if (!statementSql.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(statementSql);
                }
            }
        }
    }

    private record Migration(int version, String description, String resource) {
    }
}

