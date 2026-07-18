package com.localprojectmanager.infrastructure.database;

import com.localprojectmanager.domain.git.GitStatusCache;
import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GitStatusCacheRepositoryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void savesUpdatesReadsAndCascadesWithProjectDeletion() throws Exception {
        var database = new Database(tempDirectory.resolve("data/app.db"));
        database.initialize();
        var projectId = UUID.randomUUID();
        var projects = new ProjectRepository(database);
        projects.save(project(projectId));
        var repository = new GitStatusCacheRepository(database);
        var updatedAt = Instant.parse("2026-07-18T12:00:00Z");
        var cache = new GitStatusCache(
                projectId, "main", 3, "abc123", "Initial commit", updatedAt,
                "https://example.com/repo.git", "origin/main", 2, 1,
                2, true, updatedAt, updatedAt,
                GitStatusCache.RefreshStatus.SUCCESS, null
        );

        repository.save(cache);
        assertEquals(cache, repository.findByProjectId(projectId).orElseThrow());
        assertEquals(java.util.List.of(cache), repository.findAll());

        projects.delete(projectId);
        assertTrue(repository.findByProjectId(projectId).isEmpty());
    }

    private Project project(UUID id) {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        return new Project(
                id, "demo", "demo", tempDirectory.resolve("demo"),
                null, null, null, null, PathStatus.NORMAL,
                false, null, createdAt, createdAt
        );
    }
}
