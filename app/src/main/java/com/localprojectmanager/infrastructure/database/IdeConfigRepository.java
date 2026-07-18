package com.localprojectmanager.infrastructure.database;

import com.localprojectmanager.domain.ide.IdeConfig;
import com.localprojectmanager.domain.path.WindowsPathNormalizer;
import com.localprojectmanager.infrastructure.ide.DetectedIde;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class IdeConfigRepository {

    private final Database database;

    public IdeConfigRepository(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    public void synchronizeDetected(List<DetectedIde> detectedIdes) throws SQLException {
        var detected = List.copyOf(Objects.requireNonNull(detectedIdes));
        var now = Instant.now().toString();
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var markUnavailable = connection.prepareStatement("""
                         UPDATE ide_configs SET available = 0, updated_at = ?
                         WHERE source = 'AUTO_DETECTED'
                         """);
                 var upsert = connection.prepareStatement("""
                         INSERT INTO ide_configs (
                             id, name, ide_type, version, executable_path,
                             launch_arguments, icon_path, source, enabled,
                             available, created_at, updated_at
                         ) VALUES (?, ?, ?, ?, ?, NULL, NULL, 'AUTO_DETECTED', 1, 1, ?, ?)
                         ON CONFLICT(id) DO UPDATE SET
                             name = excluded.name,
                             ide_type = excluded.ide_type,
                             version = excluded.version,
                             executable_path = excluded.executable_path,
                             available = 1,
                             updated_at = excluded.updated_at
                         """)) {
                markUnavailable.setString(1, now);
                markUnavailable.executeUpdate();
                for (var ide : detected) {
                    upsert.setString(1, idFor(ide.executablePath()).toString());
                    upsert.setString(2, ide.name());
                    upsert.setString(3, ide.name());
                    upsert.setString(4, ide.version());
                    upsert.setString(5, ide.executablePath().toString());
                    upsert.setString(6, now);
                    upsert.setString(7, now);
                    upsert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    public List<IdeConfig> findAvailable() throws SQLException {
        var ides = new ArrayList<IdeConfig>();
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT id, name, version, executable_path, available
                     FROM ide_configs
                     WHERE enabled = 1 AND available = 1
                     ORDER BY name COLLATE NOCASE, version DESC
                     """)) {
            while (result.next()) {
                ides.add(new IdeConfig(
                        UUID.fromString(result.getString("id")),
                        result.getString("name"),
                        result.getString("version"),
                        Path.of(result.getString("executable_path")),
                        result.getInt("available") != 0
                ));
            }
        }
        return List.copyOf(ides);
    }

    public IdeConfig saveManual(String name, Path executablePath) throws SQLException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("IDE name cannot be blank");
        }
        var path = Objects.requireNonNull(executablePath).toAbsolutePath().normalize();
        var id = idFor(path);
        var now = Instant.now().toString();
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO ide_configs (
                         id, name, ide_type, version, executable_path,
                         launch_arguments, icon_path, source, enabled,
                         available, created_at, updated_at
                     ) VALUES (?, ?, 'OTHER', NULL, ?, NULL, NULL, 'MANUAL', 1, 1, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET
                         name = excluded.name,
                         executable_path = excluded.executable_path,
                         source = 'MANUAL', enabled = 1, available = 1,
                         updated_at = excluded.updated_at
                     """)) {
            statement.setString(1, id.toString());
            statement.setString(2, name.strip());
            statement.setString(3, path.toString());
            statement.setString(4, now);
            statement.setString(5, now);
            statement.executeUpdate();
        }
        return new IdeConfig(id, name, null, path, true);
    }

    public boolean isAvailable(UUID id) throws SQLException {
        return findAvailableById(id).isPresent();
    }

    public Optional<IdeConfig> findAvailableById(UUID id) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     SELECT id, name, version, executable_path, available
                     FROM ide_configs
                     WHERE id = ? AND enabled = 1 AND available = 1
                     """)) {
            statement.setString(1, Objects.requireNonNull(id).toString());
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(new IdeConfig(
                                UUID.fromString(result.getString("id")),
                                result.getString("name"),
                                result.getString("version"),
                                Path.of(result.getString("executable_path")),
                                result.getInt("available") != 0
                        ))
                        : Optional.empty();
            }
        }
    }

    private static UUID idFor(Path path) {
        return UUID.nameUUIDFromBytes(
                WindowsPathNormalizer.comparisonKey(path).getBytes(StandardCharsets.UTF_8)
        );
    }
}
