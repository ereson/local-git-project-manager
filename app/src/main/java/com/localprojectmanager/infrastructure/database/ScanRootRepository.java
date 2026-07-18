package com.localprojectmanager.infrastructure.database;

import com.localprojectmanager.domain.path.WindowsPathNormalizer;
import com.localprojectmanager.domain.scan.ScanRoot;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ScanRootRepository {

    private static final String COLUMNS = """
            id, root_path, normalized_path, enabled, last_scan_at,
            last_scan_status, last_scan_error, created_at
            """;

    private final Database database;

    public ScanRootRepository(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    public void save(ScanRoot scanRoot) throws SQLException {
        var record = ScanRootRecord.from(Objects.requireNonNull(scanRoot));
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO scan_roots (
                         id, root_path, normalized_path, enabled, last_scan_at,
                         last_scan_status, last_scan_error, created_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET
                         root_path = excluded.root_path,
                         normalized_path = excluded.normalized_path,
                         enabled = excluded.enabled,
                         last_scan_at = excluded.last_scan_at,
                         last_scan_status = excluded.last_scan_status,
                         last_scan_error = excluded.last_scan_error
                     """)) {
            record.bind(statement);
            statement.executeUpdate();
        }
    }

    public Optional<ScanRoot> findByPath(Path path) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM scan_roots WHERE normalized_path = ?"
             )) {
            statement.setString(1, WindowsPathNormalizer.comparisonKey(path));
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(ScanRootRecord.read(result).toDomain())
                        : Optional.empty();
            }
        }
    }

    public List<ScanRoot> findAll() throws SQLException {
        var scanRoots = new ArrayList<ScanRoot>();
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT " + COLUMNS + " FROM scan_roots ORDER BY created_at, id"
             )) {
            while (result.next()) {
                scanRoots.add(ScanRootRecord.read(result).toDomain());
            }
        }
        return List.copyOf(scanRoots);
    }

    public boolean delete(UUID id) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("DELETE FROM scan_roots WHERE id = ?")) {
            statement.setString(1, Objects.requireNonNull(id).toString());
            return statement.executeUpdate() == 1;
        }
    }

    private record ScanRootRecord(
            String id,
            String rootPath,
            String normalizedPath,
            boolean enabled,
            String lastScanAt,
            String lastScanStatus,
            String lastScanError,
            String createdAt
    ) {

        private static ScanRootRecord from(ScanRoot scanRoot) {
            return new ScanRootRecord(
                    scanRoot.id().toString(),
                    scanRoot.path().toString(),
                    WindowsPathNormalizer.comparisonKey(scanRoot.path()),
                    scanRoot.enabled(),
                    text(scanRoot.lastScanAt()),
                    scanRoot.lastScanStatus(),
                    scanRoot.lastScanError(),
                    scanRoot.createdAt().toString()
            );
        }

        private static ScanRootRecord read(ResultSet result) throws SQLException {
            return new ScanRootRecord(
                    result.getString("id"),
                    result.getString("root_path"),
                    result.getString("normalized_path"),
                    result.getInt("enabled") != 0,
                    result.getString("last_scan_at"),
                    result.getString("last_scan_status"),
                    result.getString("last_scan_error"),
                    result.getString("created_at")
            );
        }

        private void bind(java.sql.PreparedStatement statement) throws SQLException {
            statement.setString(1, id);
            statement.setString(2, rootPath);
            statement.setString(3, normalizedPath);
            statement.setInt(4, enabled ? 1 : 0);
            statement.setString(5, lastScanAt);
            statement.setString(6, lastScanStatus);
            statement.setString(7, lastScanError);
            statement.setString(8, createdAt);
        }

        private ScanRoot toDomain() {
            return new ScanRoot(
                    UUID.fromString(id),
                    Path.of(rootPath),
                    enabled,
                    lastScanAt == null ? null : Instant.parse(lastScanAt),
                    lastScanStatus,
                    lastScanError,
                    Instant.parse(createdAt)
            );
        }

        private static String text(Object value) {
            return value == null ? null : value.toString();
        }
    }
}

