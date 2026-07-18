package com.localprojectmanager.infrastructure.database;

import com.localprojectmanager.domain.path.WindowsPathNormalizer;
import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.domain.project.PullStrategy;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProjectRepository {

    private static final String COLUMNS = """
            id, display_name, directory_name, project_path, normalized_path,
            scan_root_id, default_ide_id, pull_strategy, last_opened_at,
            path_status, is_nested_repository, parent_repository,
            created_at, updated_at
            """;
    private static final String INSERT = """
            INSERT INTO projects (
                id, display_name, directory_name, project_path, normalized_path,
                scan_root_id, default_ide_id, pull_strategy, last_opened_at,
                path_status, is_nested_repository, parent_repository,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final Database database;

    public ProjectRepository(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    public void save(Project project) throws SQLException {
        var record = ProjectRecord.from(Objects.requireNonNull(project));
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(INSERT + """
                     ON CONFLICT(id) DO UPDATE SET
                         display_name = excluded.display_name,
                         directory_name = excluded.directory_name,
                         project_path = excluded.project_path,
                         normalized_path = excluded.normalized_path,
                         scan_root_id = excluded.scan_root_id,
                         default_ide_id = excluded.default_ide_id,
                         pull_strategy = excluded.pull_strategy,
                         last_opened_at = excluded.last_opened_at,
                         path_status = excluded.path_status,
                         is_nested_repository = excluded.is_nested_repository,
                         parent_repository = excluded.parent_repository,
                         created_at = excluded.created_at,
                         updated_at = excluded.updated_at
                     """)) {
            record.bind(statement);
            statement.executeUpdate();
        }
    }

    public int insertIgnoringDuplicatePaths(List<Project> projects) throws SQLException {
        var records = Objects.requireNonNull(projects).stream()
                .map(project -> ProjectRecord.from(Objects.requireNonNull(project)))
                .toList();
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    INSERT + "ON CONFLICT(normalized_path) DO NOTHING"
            )) {
                var inserted = 0;
                for (var record : records) {
                    record.bind(statement);
                    inserted += statement.executeUpdate();
                }
                connection.commit();
                return inserted;
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

    public Optional<Project> findById(UUID id) throws SQLException {
        return findOne("SELECT " + COLUMNS + " FROM projects WHERE id = ?", id.toString());
    }

    public Optional<Project> findByPath(Path path) throws SQLException {
        return findOne(
                "SELECT " + COLUMNS + " FROM projects WHERE normalized_path = ?",
                WindowsPathNormalizer.comparisonKey(path)
        );
    }

    public List<Project> findAll() throws SQLException {
        var projects = new ArrayList<Project>();
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT " + COLUMNS + " FROM projects ORDER BY created_at, id"
             )) {
            while (result.next()) {
                projects.add(ProjectRecord.read(result).toDomain());
            }
        }
        return List.copyOf(projects);
    }

    public boolean delete(UUID id) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("DELETE FROM projects WHERE id = ?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() == 1;
        }
    }

    private Optional<Project> findOne(String sql, String parameter) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(ProjectRecord.read(result).toDomain())
                        : Optional.empty();
            }
        }
    }

    private record ProjectRecord(
            String id,
            String displayName,
            String directoryName,
            String projectPath,
            String normalizedPath,
            String scanRootId,
            String defaultIdeId,
            String pullStrategy,
            String lastOpenedAt,
            String pathStatus,
            boolean nestedRepository,
            String parentRepository,
            String createdAt,
            String updatedAt
    ) {

        private static ProjectRecord from(Project project) {
            return new ProjectRecord(
                    project.id().toString(),
                    project.displayName(),
                    project.directoryName(),
                    project.path().toString(),
                    WindowsPathNormalizer.comparisonKey(project.path()),
                    text(project.scanRootId()),
                    text(project.defaultIdeId()),
                    project.pullStrategy() == null ? null : project.pullStrategy().name(),
                    text(project.lastOpenedAt()),
                    project.pathStatus().name(),
                    project.nestedRepository(),
                    text(project.parentRepositoryPath()),
                    project.createdAt().toString(),
                    project.updatedAt().toString()
            );
        }

        private static ProjectRecord read(ResultSet result) throws SQLException {
            return new ProjectRecord(
                    result.getString("id"),
                    result.getString("display_name"),
                    result.getString("directory_name"),
                    result.getString("project_path"),
                    result.getString("normalized_path"),
                    result.getString("scan_root_id"),
                    result.getString("default_ide_id"),
                    result.getString("pull_strategy"),
                    result.getString("last_opened_at"),
                    result.getString("path_status"),
                    result.getInt("is_nested_repository") != 0,
                    result.getString("parent_repository"),
                    result.getString("created_at"),
                    result.getString("updated_at")
            );
        }

        private void bind(java.sql.PreparedStatement statement) throws SQLException {
            statement.setString(1, id);
            statement.setString(2, displayName);
            statement.setString(3, directoryName);
            statement.setString(4, projectPath);
            statement.setString(5, normalizedPath);
            statement.setString(6, scanRootId);
            statement.setString(7, defaultIdeId);
            statement.setString(8, pullStrategy);
            statement.setString(9, lastOpenedAt);
            statement.setString(10, pathStatus);
            statement.setInt(11, nestedRepository ? 1 : 0);
            statement.setString(12, parentRepository);
            statement.setString(13, createdAt);
            statement.setString(14, updatedAt);
        }

        private Project toDomain() {
            return new Project(
                    UUID.fromString(id),
                    displayName,
                    directoryName,
                    Path.of(projectPath),
                    uuid(scanRootId),
                    uuid(defaultIdeId),
                    pullStrategy == null ? null : PullStrategy.valueOf(pullStrategy),
                    lastOpenedAt == null ? null : Instant.parse(lastOpenedAt),
                    PathStatus.valueOf(pathStatus),
                    nestedRepository,
                    parentRepository == null ? null : Path.of(parentRepository),
                    Instant.parse(createdAt),
                    Instant.parse(updatedAt)
            );
        }

        private static String text(Object value) {
            return value == null ? null : value.toString();
        }

        private static UUID uuid(String value) {
            return value == null ? null : UUID.fromString(value);
        }
    }
}
