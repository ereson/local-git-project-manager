package com.localprojectmanager.infrastructure.database;

import com.localprojectmanager.domain.git.GitStatusCache;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GitStatusCacheRepository {

    private static final String COLUMNS = """
            project_id, current_branch, uncommitted_file_count,
            latest_commit_hash, latest_commit_message, latest_commit_time,
            remote_url, upstream_branch, ahead_count, behind_count,
            conflict_file_count, has_conflict, remote_status_updated_at,
            local_status_updated_at, refresh_status, refresh_error
            """;

    private final Database database;

    public GitStatusCacheRepository(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    public void save(GitStatusCache cache) throws SQLException {
        Objects.requireNonNull(cache);
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO git_status_cache (
                         project_id, current_branch, uncommitted_file_count,
                         latest_commit_hash, latest_commit_message, latest_commit_time,
                         remote_url, upstream_branch, ahead_count, behind_count,
                         conflict_file_count, has_conflict, remote_status_updated_at,
                         local_status_updated_at,
                         refresh_status, refresh_error
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(project_id) DO UPDATE SET
                         current_branch = excluded.current_branch,
                         uncommitted_file_count = excluded.uncommitted_file_count,
                         latest_commit_hash = excluded.latest_commit_hash,
                         latest_commit_message = excluded.latest_commit_message,
                         latest_commit_time = excluded.latest_commit_time,
                         remote_url = excluded.remote_url,
                         upstream_branch = excluded.upstream_branch,
                         ahead_count = excluded.ahead_count,
                         behind_count = excluded.behind_count,
                         conflict_file_count = excluded.conflict_file_count,
                         has_conflict = excluded.has_conflict,
                         remote_status_updated_at = excluded.remote_status_updated_at,
                         local_status_updated_at = excluded.local_status_updated_at,
                         refresh_status = excluded.refresh_status,
                         refresh_error = excluded.refresh_error
                     """)) {
            statement.setString(1, cache.projectId().toString());
            statement.setString(2, cache.currentBranch());
            statement.setInt(3, cache.uncommittedFileCount());
            statement.setString(4, cache.latestCommitHash());
            statement.setString(5, cache.latestCommitMessage());
            statement.setString(6, text(cache.latestCommitTime()));
            statement.setString(7, cache.remoteUrl());
            statement.setString(8, cache.upstreamBranch());
            setInteger(statement, 9, cache.aheadCount());
            setInteger(statement, 10, cache.behindCount());
            statement.setInt(11, cache.conflictFileCount());
            statement.setInt(12, cache.hasConflict() ? 1 : 0);
            statement.setString(13, text(cache.remoteStatusUpdatedAt()));
            statement.setString(14, text(cache.localStatusUpdatedAt()));
            statement.setString(15, cache.refreshStatus().name());
            statement.setString(16, cache.refreshError());
            statement.executeUpdate();
        }
    }

    public Optional<GitStatusCache> findByProjectId(UUID projectId) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "SELECT " + COLUMNS + " FROM git_status_cache WHERE project_id = ?"
             )) {
            statement.setString(1, projectId.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    public List<GitStatusCache> findAll() throws SQLException {
        var caches = new ArrayList<GitStatusCache>();
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT " + COLUMNS + " FROM git_status_cache ORDER BY project_id"
             )) {
            while (result.next()) {
                caches.add(read(result));
            }
        }
        return List.copyOf(caches);
    }

    private static GitStatusCache read(ResultSet result) throws SQLException {
        return new GitStatusCache(
                UUID.fromString(result.getString("project_id")),
                result.getString("current_branch"),
                result.getInt("uncommitted_file_count"),
                result.getString("latest_commit_hash"),
                result.getString("latest_commit_message"),
                instant(result.getString("latest_commit_time")),
                result.getString("remote_url"),
                result.getString("upstream_branch"),
                integer(result, "ahead_count"),
                integer(result, "behind_count"),
                result.getInt("conflict_file_count"),
                result.getInt("has_conflict") != 0,
                instant(result.getString("remote_status_updated_at")),
                instant(result.getString("local_status_updated_at")),
                GitStatusCache.RefreshStatus.valueOf(result.getString("refresh_status")),
                result.getString("refresh_error")
        );
    }

    private static String text(Instant value) {
        return value == null ? null : value.toString();
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static void setInteger(
            java.sql.PreparedStatement statement,
            int index,
            Integer value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static Integer integer(ResultSet result, String column) throws SQLException {
        var value = result.getInt(column);
        return result.wasNull() ? null : value;
    }
}
