package com.localprojectmanager.application.git;

import com.localprojectmanager.domain.git.GitOperationRecord;
import com.localprojectmanager.domain.git.GitStatusCache;
import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.domain.project.PullStrategy;
import com.localprojectmanager.infrastructure.database.Database;
import com.localprojectmanager.infrastructure.database.GitOperationRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GitOperationServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void fetchesPullsAndSwitchesAgainstALocalRemote() throws Exception {
        var remote = tempDirectory.resolve("remote repository.git");
        var local = tempDirectory.resolve("local repository");
        var contributor = tempDirectory.resolve("contributor");
        git("init", "--bare", remote.toString());
        git("init", "-b", "main", local.toString());
        configureIdentity(local);
        Files.writeString(local.resolve("README.md"), "initial");
        git("-C", local.toString(), "add", "README.md");
        git("-C", local.toString(), "commit", "-m", "Initial commit");
        git("-C", local.toString(), "remote", "add", "origin", remote.toString());
        git("-C", local.toString(), "push", "-u", "origin", "main");
        git("--git-dir", remote.toString(), "symbolic-ref", "HEAD", "refs/heads/main");

        git("clone", remote.toString(), contributor.toString());
        configureIdentity(contributor);
        Files.writeString(contributor.resolve("incoming.txt"), "remote change");
        git("-C", contributor.toString(), "add", "incoming.txt");
        git("-C", contributor.toString(), "commit", "-m", "Remote change");
        git("-C", contributor.toString(), "push");

        var database = new Database(tempDirectory.resolve("data/app.db"));
        database.initialize();
        var project = project(local);
        new ProjectRepository(database).save(project);
        var caches = new GitStatusCacheRepository(database);
        caches.save(GitStatusCache.pending(project.id()));
        var operations = new GitOperationRepository(database);
        var git = new GitClient();

        try (var service = new GitOperationService(
                git, new ProjectGitLock(), caches, operations
        )) {
            assertTrue(service.fetch(project).join().successful());
            var fetched = caches.findByProjectId(project.id()).orElseThrow();
            assertEquals(0, fetched.aheadCount());
            assertEquals(1, fetched.behindCount());

            assertTrue(service.pull(project, PullStrategy.REBASE).join().successful());
            assertTrue(Files.isRegularFile(local.resolve("incoming.txt")));

            git("-C", local.toString(), "branch", "feature/test");
            assertTrue(service.switchLocalBranch(project, "feature/test", false)
                    .join().successful());
            assertEquals("feature/test", git.getCurrentBranch(local).orElseThrow().value());
        }

        var latest = operations.findByProjectId(project.id()).orElseThrow();
        assertEquals(GitOperationRecord.Type.SWITCH_BRANCH, latest.type());
        assertEquals(GitOperationRecord.Status.SUCCESS, latest.status());
    }

    private void configureIdentity(Path repository) throws Exception {
        git("-C", repository.toString(), "config", "user.name", "Test User");
        git("-C", repository.toString(), "config", "user.email", "test@example.com");
    }

    private void git(String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(arguments));
        var result = new CommandExecutor().execute(command, null, Duration.ofSeconds(20));
        if (!result.successful()) {
            throw new AssertionError(new String(result.stderr()));
        }
    }

    private static Project project(Path path) {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        return new Project(
                UUID.randomUUID(), "demo", "demo", path,
                null, null, PullStrategy.REBASE, null, PathStatus.NORMAL,
                false, null, createdAt, createdAt
        );
    }
}
