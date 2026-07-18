package com.localprojectmanager.application.git;

import com.localprojectmanager.domain.git.GitStatusCache;
import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.infrastructure.database.Database;
import com.localprojectmanager.infrastructure.database.GitStatusCacheRepository;
import com.localprojectmanager.infrastructure.database.ProjectRepository;
import com.localprojectmanager.infrastructure.git.CommandExecutor;
import com.localprojectmanager.infrastructure.git.GitClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GitStatusServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void refreshesRepositoriesInBackgroundAndIsolatesFailures() throws Exception {
        var database = new Database(tempDirectory.resolve("data/app.db"));
        database.initialize();
        var projects = new ProjectRepository(database);
        var repository = tempDirectory.resolve("repo");
        Files.createDirectories(repository);
        git("init", "-b", "main", repository.toString());
        Files.writeString(repository.resolve("tracked.txt"), "tracked");
        git("-C", repository.toString(), "add", "tracked.txt");
        git("-C", repository.toString(), "-c", "user.name=Test",
                "-c", "user.email=test@example.com", "commit", "-m", "Initial commit");
        Files.writeString(repository.resolve("untracked.txt"), "untracked");
        var valid = project(repository);
        var invalid = project(tempDirectory.resolve("not-a-repository"));
        projects.save(valid);
        projects.save(invalid);
        var caches = new GitStatusCacheRepository(database);

        try (var service = new GitStatusService(new GitClient(), projects, caches)) {
            service.refreshAll().join();
        }

        var refreshed = caches.findByProjectId(valid.id()).orElseThrow();
        assertEquals(GitStatusCache.RefreshStatus.SUCCESS, refreshed.refreshStatus());
        assertEquals("main", refreshed.currentBranch());
        assertEquals(1, refreshed.uncommittedFileCount());
        assertNotNull(refreshed.latestCommitHash());
        assertEquals(GitStatusCache.RefreshStatus.FAILED,
                caches.findByProjectId(invalid.id()).orElseThrow().refreshStatus());
    }

    private void git(String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(arguments));
        var result = new CommandExecutor().execute(command, null, Duration.ofSeconds(10));
        if (!result.successful()) {
            throw new AssertionError(new String(result.stderr()));
        }
    }

    private static Project project(Path path) {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        return new Project(
                UUID.randomUUID(), path.getFileName().toString(), path.getFileName().toString(),
                path, null, null, null, null, PathStatus.NORMAL,
                false, null, createdAt, createdAt
        );
    }
}
