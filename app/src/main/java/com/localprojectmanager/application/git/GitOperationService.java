package com.localprojectmanager.application.git;

import com.localprojectmanager.domain.project.PathStatus;
import com.localprojectmanager.domain.project.Project;
import com.localprojectmanager.domain.project.PullStrategy;
import com.localprojectmanager.infrastructure.git.GitClient;
import com.localprojectmanager.infrastructure.database.GitStatusCacheRepository;
import com.localprojectmanager.domain.git.GitStatusCache;
import com.localprojectmanager.domain.git.GitOperationRecord;
import com.localprojectmanager.infrastructure.database.GitOperationRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GitOperationService implements AutoCloseable {

    private final GitClient git;
    private final ProjectGitLock locks;
    private final GitStatusCacheRepository caches;
    private final GitOperationRepository operations;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public GitOperationService(
            GitClient git,
            ProjectGitLock locks,
            GitStatusCacheRepository caches,
            GitOperationRepository operations
    ) {
        this.git = Objects.requireNonNull(git);
        this.locks = Objects.requireNonNull(locks);
        this.caches = Objects.requireNonNull(caches);
        this.operations = Objects.requireNonNull(operations);
    }

    public CompletableFuture<Outcome> fetch(Project project) {
        Objects.requireNonNull(project);
        if (project.pathStatus() != PathStatus.NORMAL || !Files.isDirectory(project.path())) {
            return CompletableFuture.completedFuture(
                    new Outcome(false, false, "项目路径当前不可用。")
            );
        }
        var startedAt = java.time.Instant.now();
        return CompletableFuture.supplyAsync(() -> {
            var lock = locks.get(project.id());
            lock.lock();
            try {
                var result = git.fetch(project.path());
                if (result.successful()) {
                    var upstream = git.getUpstream(project.path()).orElse(null);
                    var divergence = git.getAheadBehind(project.path()).orElse(null);
                    var cached = caches.findByProjectId(project.id())
                            .orElseGet(() -> GitStatusCache.pending(project.id()));
                    caches.save(cached.withRemoteStatus(
                            upstream,
                            divergence == null ? null : divergence.ahead(),
                            divergence == null ? null : divergence.behind(),
                            java.time.Instant.now()
                    ));
                    return new Outcome(true, false, "远程更新检查完成。");
                }
                return new Outcome(
                        false,
                        result.timedOut(),
                        result.timedOut() ? "检查远程更新超时。" : error(result.stderr())
                );
            } catch (IOException exception) {
                return new Outcome(false, false, "无法启动 Git，请检查 Git 安装。");
            } catch (java.sql.SQLException exception) {
                return new Outcome(false, false, "远程状态保存失败。");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new Outcome(false, false, "操作已取消。");
            } finally {
                lock.unlock();
            }
        }, executor).whenComplete((outcome, failure) -> record(
                project, GitOperationRecord.Type.FETCH, startedAt,
                outcome == null ? null : outcome.successful(),
                outcome == null ? null : outcome.message(), failure
        ));
    }

    public CompletableFuture<BranchLists> listBranches(Project project) {
        Objects.requireNonNull(project);
        if (project.pathStatus() != PathStatus.NORMAL || !Files.isDirectory(project.path())) {
            return CompletableFuture.completedFuture(new BranchLists(List.of(), List.of()));
        }
        return CompletableFuture.supplyAsync(() -> {
            var lock = locks.get(project.id());
            lock.lock();
            try {
                return new BranchLists(
                        git.listLocalBranches(project.path()),
                        git.listRemoteBranches(project.path())
                );
            } finally {
                lock.unlock();
            }
        }, executor);
    }

    public CompletableFuture<SwitchOutcome> switchLocalBranch(
            Project project,
            String branch,
            boolean allowDirty
    ) {
        return switchBranch(project, branch, false, allowDirty);
    }

    public CompletableFuture<SwitchOutcome> createRemoteTrackingBranch(
            Project project,
            String remoteBranch,
            boolean allowDirty
    ) {
        return switchBranch(project, remoteBranch, true, allowDirty);
    }

    public CompletableFuture<Outcome> pull(Project project, PullStrategy strategy) {
        Objects.requireNonNull(project);
        Objects.requireNonNull(strategy);
        if (project.pathStatus() != PathStatus.NORMAL || !Files.isDirectory(project.path())) {
            return CompletableFuture.completedFuture(
                    new Outcome(false, false, "项目路径当前不可用。")
            );
        }
        var startedAt = java.time.Instant.now();
        return CompletableFuture.supplyAsync(() -> {
            var lock = locks.get(project.id());
            lock.lock();
            try {
                var head = git.getCurrentBranch(project.path());
                if (head.isEmpty() || head.get().detached()) {
                    return new Outcome(false, false, "Detached HEAD 状态下不能 Pull。");
                }
                if (git.getUpstream(project.path()).isEmpty()) {
                    return new Outcome(false, false, "当前分支未设置远程跟踪分支。");
                }
                if (caches.findByProjectId(project.id()).map(GitStatusCache::hasConflict)
                        .orElse(false)) {
                    return new Outcome(false, false, "项目存在未处理冲突，已阻止 Pull。");
                }
                var modifications = git.getUncommittedFileCount(project.path());
                if (modifications.isEmpty() || modifications.getAsInt() > 0) {
                    return new Outcome(false, false, "存在未提交修改，已阻止 Pull。");
                }
                var fetch = git.fetch(project.path());
                if (!fetch.successful()) {
                    return new Outcome(false, fetch.timedOut(), error(fetch.stderr()));
                }
                var result = git.pull(project.path(), strategy);
                if (!result.successful()) {
                    var conflict = git.getConflict(project.path()).orElse(null);
                    if (conflict != null && conflict.hasConflict()) {
                        var cached = caches.findByProjectId(project.id())
                                .orElseGet(() -> GitStatusCache.pending(project.id()));
                        caches.save(cached.withConflict(conflict.fileCount(), true));
                    }
                }
                return result.successful()
                        ? new Outcome(true, false, "Pull 完成。")
                        : new Outcome(false, result.timedOut(), error(result.stderr()));
            } catch (IOException exception) {
                return new Outcome(false, false, "无法启动 Git，请检查 Git 安装。");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new Outcome(false, false, "操作已取消。");
            } catch (java.sql.SQLException exception) {
                return new Outcome(false, false, "无法读取冲突状态。");
            } finally {
                lock.unlock();
            }
        }, executor).whenComplete((outcome, failure) -> record(
                project, GitOperationRecord.Type.PULL, startedAt,
                outcome == null ? null : outcome.successful(),
                outcome == null ? null : outcome.message(), failure
        ));
    }

    private CompletableFuture<SwitchOutcome> switchBranch(
            Project project,
            String branch,
            boolean remote,
            boolean allowDirty
    ) {
        Objects.requireNonNull(project);
        Objects.requireNonNull(branch);
        if (project.pathStatus() != PathStatus.NORMAL || !Files.isDirectory(project.path())) {
            return CompletableFuture.completedFuture(
                    new SwitchOutcome(false, false, "项目路径当前不可用。")
            );
        }
        var startedAt = java.time.Instant.now();
        return CompletableFuture.supplyAsync(() -> {
            var lock = locks.get(project.id());
            lock.lock();
            try {
                var available = remote
                        ? git.listRemoteBranches(project.path())
                        : git.listLocalBranches(project.path());
                if (!available.contains(branch)) {
                    return new SwitchOutcome(false, false, "所选分支已不可用，请刷新列表。");
                }
                var modifications = git.getUncommittedFileCount(project.path());
                if (modifications.isEmpty()) {
                    return new SwitchOutcome(false, false, "无法读取本地修改状态。");
                }
                if (modifications.getAsInt() > 0 && !allowDirty) {
                    return new SwitchOutcome(
                            false, true,
                            "当前有 " + modifications.getAsInt() + " 个未提交文件，继续切换可能失败。"
                    );
                }
                var result = remote
                        ? createTracking(project, branch)
                        : git.switchBranch(project.path(), branch);
                return result.successful()
                        ? new SwitchOutcome(true, false, "已切换到 " + branch + "。")
                        : new SwitchOutcome(false, false, error(result.stderr()));
            } catch (IOException exception) {
                return new SwitchOutcome(false, false, "无法启动 Git，请检查 Git 安装。");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new SwitchOutcome(false, false, "操作已取消。");
            } finally {
                lock.unlock();
            }
        }, executor).whenComplete((outcome, failure) -> record(
                project, GitOperationRecord.Type.SWITCH_BRANCH, startedAt,
                outcome == null ? null : outcome.successful(),
                outcome == null ? null : outcome.message(), failure
        ));
    }

    public java.util.Optional<GitOperationRecord> lastOperation(java.util.UUID projectId)
            throws java.sql.SQLException {
        return operations.findByProjectId(projectId);
    }

    private void record(
            Project project,
            GitOperationRecord.Type type,
            java.time.Instant startedAt,
            Boolean successful,
            String message,
            Throwable failure
    ) {
        try {
            var conflict = caches.findByProjectId(project.id())
                    .map(GitStatusCache::hasConflict)
                    .orElse(false);
            var status = conflict
                    ? GitOperationRecord.Status.CONFLICT
                    : Boolean.TRUE.equals(successful)
                            ? GitOperationRecord.Status.SUCCESS
                            : GitOperationRecord.Status.FAILED;
            operations.save(new GitOperationRecord(
                    project.id(), type, startedAt, java.time.Instant.now(), status,
                    message,
                    status == GitOperationRecord.Status.SUCCESS
                            ? null
                            : failure == null ? message : failure.getMessage()
            ));
        } catch (java.sql.SQLException ignored) {
            // An operation result remains valid even if its optional summary cannot be saved.
        }
    }

    private com.localprojectmanager.infrastructure.git.CommandResult createTracking(
            Project project,
            String remoteBranch
    ) throws IOException, InterruptedException {
        var slash = remoteBranch.indexOf('/');
        if (slash < 1 || slash == remoteBranch.length() - 1) {
            return new com.localprojectmanager.infrastructure.git.CommandResult(
                    1, new byte[0], "远程分支名称无效。".getBytes(StandardCharsets.UTF_8),
                    java.time.Duration.ZERO, false
            );
        }
        var localBranch = remoteBranch.substring(slash + 1);
        if (git.listLocalBranches(project.path()).contains(localBranch)) {
            return new com.localprojectmanager.infrastructure.git.CommandResult(
                    1, new byte[0], "对应本地分支已存在，请从本地列表选择。".getBytes(StandardCharsets.UTF_8),
                    java.time.Duration.ZERO, false
            );
        }
        return git.createTrackingBranch(project.path(), localBranch, remoteBranch);
    }

    private static String error(byte[] stderr) {
        var message = new String(stderr, StandardCharsets.UTF_8).trim();
        if (message.isBlank()) {
            return "Git 操作失败，未返回详细信息。";
        }
        var normalized = message.toLowerCase(Locale.ROOT);
        var explanation = normalized.contains("authentication failed")
                || normalized.contains("permission denied")
                ? "Git 认证失败，请在 IDE 或终端中完成认证。"
                : normalized.contains("could not resolve host")
                        || normalized.contains("failed to connect")
                        || normalized.contains("network is unreachable")
                        ? "网络不可用或无法访问远程仓库。"
                        : normalized.contains("repository not found")
                                ? "远程仓库不存在或当前账号没有访问权限。"
                                : normalized.contains("not a git repository")
                                        ? "所选目录不再是有效的 Git 仓库。"
                                        : "Git 操作失败。";
        return explanation + "\n\nGit 原始输出：\n" + message;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    public record Outcome(boolean successful, boolean timedOut, String message) {
    }

    public record BranchLists(List<String> local, List<String> remote) {

        public BranchLists {
            local = List.copyOf(local);
            remote = List.copyOf(remote);
        }
    }

    public record SwitchOutcome(
            boolean successful,
            boolean confirmationRequired,
            String message
    ) {
    }
}
