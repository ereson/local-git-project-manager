package com.localprojectmanager.application.git;

import com.localprojectmanager.domain.git.GitStatusCache;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.infrastructure.database.GitStatusCacheRepository;
import com.localprojectmanager.infrastructure.database.ProjectRepository;
import com.localprojectmanager.infrastructure.git.GitClient;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class GitStatusService implements AutoCloseable {

    private final GitClient git;
    private final ProjectRepository projects;
    private final GitStatusCacheRepository caches;
    private final ExecutorService executor;
    private final ExecutorService priorityExecutor;
    private final ProjectGitLock projectLocks;

    public GitStatusService(
            GitClient git,
            ProjectRepository projects,
            GitStatusCacheRepository caches
    ) {
        this(
                git, projects, caches,
                Executors.newFixedThreadPool(3),
                Executors.newSingleThreadExecutor(),
                new ProjectGitLock()
        );
    }

    public GitStatusService(
            GitClient git,
            ProjectRepository projects,
            GitStatusCacheRepository caches,
            ProjectGitLock projectLocks
    ) {
        this(
                git, projects, caches,
                Executors.newFixedThreadPool(3),
                Executors.newSingleThreadExecutor(),
                projectLocks
        );
    }

    GitStatusService(
            GitClient git,
            ProjectRepository projects,
            GitStatusCacheRepository caches,
            ExecutorService executor,
            ExecutorService priorityExecutor,
            ProjectGitLock projectLocks
    ) {
        this.git = Objects.requireNonNull(git);
        this.projects = Objects.requireNonNull(projects);
        this.caches = Objects.requireNonNull(caches);
        this.executor = Objects.requireNonNull(executor);
        this.priorityExecutor = Objects.requireNonNull(priorityExecutor);
        this.projectLocks = Objects.requireNonNull(projectLocks);
    }

    public Map<UUID, GitStatusCache> cachedStatuses() throws SQLException {
        return caches.findAll().stream().collect(Collectors.toUnmodifiableMap(
                GitStatusCache::projectId,
                cache -> cache
        ));
    }

    public CompletableFuture<Void> refreshAll() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return projects.findAll();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor).thenCompose(found -> CompletableFuture.allOf(found.stream()
                .map(project -> CompletableFuture.runAsync(() -> refreshSafely(project), executor))
                .toArray(CompletableFuture[]::new)));
    }

    public CompletableFuture<GitStatusCache> refresh(Project project) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return refreshNow(project);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, priorityExecutor);
    }

    private void refreshSafely(Project project) {
        try {
            refreshNow(project);
        } catch (SQLException ignored) {
            // One database or project failure must not stop the remaining refreshes.
        }
    }

    private GitStatusCache refreshNow(Project project) throws SQLException {
        var lock = projectLocks.get(project.id());
        lock.lock();
        try {
            return refreshLocked(project);
        } finally {
            lock.unlock();
        }
    }

    private GitStatusCache refreshLocked(Project project) throws SQLException {
        var previous = caches.findByProjectId(project.id()).orElse(null);
        var head = git.getCurrentBranch(project.path()).orElse(null);
        var count = git.getUncommittedFileCount(project.path());
        if (count.isEmpty()) {
            var failed = new GitStatusCache(
                    project.id(),
                    previous == null ? null : previous.currentBranch(),
                    previous == null ? 0 : previous.uncommittedFileCount(),
                    previous == null ? null : previous.latestCommitHash(),
                    previous == null ? null : previous.latestCommitMessage(),
                    previous == null ? null : previous.latestCommitTime(),
                    previous == null ? null : previous.remoteUrl(),
                    previous == null ? null : previous.upstreamBranch(),
                    previous == null ? null : previous.aheadCount(),
                    previous == null ? null : previous.behindCount(),
                    previous == null ? 0 : previous.conflictFileCount(),
                    previous != null && previous.hasConflict(),
                    previous == null ? null : previous.remoteStatusUpdatedAt(),
                    previous == null ? null : previous.localStatusUpdatedAt(),
                    GitStatusCache.RefreshStatus.FAILED,
                    "无法读取本地 Git 状态"
            );
            caches.save(failed);
            return failed;
        }

        var commit = git.getLatestCommit(project.path()).orElse(null);
        var conflict = git.getConflict(project.path()).orElse(null);
        var refreshed = new GitStatusCache(
                project.id(),
                head == null ? null : head.displayName(),
                count.getAsInt(),
                commit == null ? null : commit.hash(),
                commit == null ? null : commit.message(),
                commit == null ? null : commit.committedAt(),
                git.getRemoteUrl(project.path()).orElse(null),
                previous == null ? null : previous.upstreamBranch(),
                previous == null ? null : previous.aheadCount(),
                previous == null ? null : previous.behindCount(),
                conflict == null ? 0 : conflict.fileCount(),
                conflict != null && conflict.hasConflict(),
                previous == null ? null : previous.remoteStatusUpdatedAt(),
                Instant.now(),
                GitStatusCache.RefreshStatus.SUCCESS,
                null
        );
        caches.save(refreshed);
        return refreshed;
    }

    @Override
    public void close() {
        executor.shutdownNow();
        priorityExecutor.shutdownNow();
    }
}
