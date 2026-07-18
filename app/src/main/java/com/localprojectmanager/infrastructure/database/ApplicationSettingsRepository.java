package com.localprojectmanager.infrastructure.database;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ApplicationSettingsRepository {

    private final Database database;

    public ApplicationSettingsRepository(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    public Optional<String> get(String key) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     SELECT setting_value FROM application_settings WHERE setting_key = ?
                     """)) {
            statement.setString(1, Objects.requireNonNull(key));
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.ofNullable(result.getString(1)) : Optional.empty();
            }
        }
    }

    public String get(String key, String defaultValue) throws SQLException {
        return get(key).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) throws SQLException {
        return get(key)
                .map(value -> switch (value.toLowerCase()) {
                    case "true" -> true;
                    case "false" -> false;
                    default -> defaultValue;
                })
                .orElse(defaultValue);
    }

    public <E extends Enum<E>> E getEnum(String key, Class<E> type, E defaultValue) throws SQLException {
        return get(key).map(value -> {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException exception) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    public void put(String key, String value) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO application_settings (setting_key, setting_value, updated_at)
                     VALUES (?, ?, ?)
                     ON CONFLICT(setting_key) DO UPDATE SET
                         setting_value = excluded.setting_value,
                         updated_at = excluded.updated_at
                     """)) {
            statement.setString(1, Objects.requireNonNull(key));
            statement.setString(2, Objects.requireNonNull(value));
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        }
    }
}
