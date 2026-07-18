package com.localprojectmanager.infrastructure.database;

import com.localprojectmanager.domain.git.GitOperationRecord;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GitOperationRepository {

    private final Database database;

    public GitOperationRepository(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    public void save(GitOperationRecord operation) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO last_git_operations (
                         project_id, operation_type, started_at, finished_at,
                         status, summary, raw_error
                     ) VALUES (?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(project_id) DO UPDATE SET
                         operation_type = excluded.operation_type,
                         started_at = excluded.started_at,
                         finished_at = excluded.finished_at,
                         status = excluded.status,
                         summary = excluded.summary,
                         raw_error = excluded.raw_error
                     """)) {
            statement.setString(1, operation.projectId().toString());
            statement.setString(2, operation.type().name());
            statement.setString(3, operation.startedAt().toString());
            statement.setString(4, operation.finishedAt().toString());
            statement.setString(5, operation.status().name());
            statement.setString(6, operation.summary());
            statement.setString(7, operation.rawError());
            statement.executeUpdate();
        }
    }

    public Optional<GitOperationRecord> findByProjectId(UUID projectId) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     SELECT operation_type, started_at, finished_at, status, summary, raw_error
                     FROM last_git_operations WHERE project_id = ?
                     """)) {
            statement.setString(1, projectId.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(new GitOperationRecord(
                        projectId,
                        GitOperationRecord.Type.valueOf(result.getString("operation_type")),
                        Instant.parse(result.getString("started_at")),
                        Instant.parse(result.getString("finished_at")),
                        GitOperationRecord.Status.valueOf(result.getString("status")),
                        result.getString("summary"),
                        result.getString("raw_error")
                )) : Optional.empty();
            }
        }
    }
}
