package com.localprojectmanager.infrastructure.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class Database {

    private final Path file;

    public Database(Path file) {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
    }

    public static Database forCurrentUser() {
        return new Database(applicationDirectory(false).resolve("data/app.db"));
    }

    public static Database forRuntime(boolean portable) {
        return new Database(applicationDirectory(portable).resolve("data/app.db"));
    }

    public static Path applicationDirectory(boolean portable) {
        if (portable) {
            return Path.of(System.getProperty("lpm.portable.dir", System.getProperty("user.dir")))
                    .toAbsolutePath().normalize();
        }
        var localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            throw new IllegalStateException("LOCALAPPDATA is not defined");
        }
        return Path.of(localAppData, "LocalProjectManager");
    }

    public void initialize() throws IOException, SQLException {
        Files.createDirectories(file.getParent());
        try (var connection = openConnection()) {
            DatabaseMigrator.migrate(connection);
        }
    }

    public Connection openConnection() throws SQLException {
        var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }
}
