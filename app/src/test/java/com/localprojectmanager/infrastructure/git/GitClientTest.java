package com.localprojectmanager.infrastructure.git;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.localprojectmanager.domain.project.PullStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GitClientTest {

    @Test
    void detectsGitVersionAndTreatsFailedCommandsAsUnavailable() {
        var command = new AtomicReference<List<String>>();
        GitClient.CommandRunner installed = (arguments, directory, timeout) -> {
            command.set(arguments);
            return new CommandResult(
                    0,
                    "git version 2.47.1.windows.2\n".getBytes(StandardCharsets.UTF_8),
                    new byte[0],
                    Duration.ofMillis(1),
                    false
            );
        };

        assertEquals("2.47.1.windows.2",
                new GitClient("git", installed).getVersion().orElseThrow().value());
        assertEquals(List.of("git", "--version"), command.get());
        assertTrue(new GitClient("git", (arguments, directory, timeout) ->
                new CommandResult(1, new byte[0], new byte[0], timeout, false)
        ).getVersion().isEmpty());
    }

    @Test
    void readsNamedAndDetachedHeads() {
        var project = Path.of("D:\\项目 1");
        var commands = new ArrayList<List<String>>();
        GitClient.CommandRunner namedBranch = (arguments, directory, timeout) -> {
            commands.add(arguments);
            return success("feature/中文\n");
        };

        var named = new GitClient("git", namedBranch).getCurrentBranch(project).orElseThrow();

        assertEquals("feature/中文", named.displayName());
        assertEquals(List.of(
                "git", "-C", project.toString(), "branch", "--show-current"
        ), commands.getFirst());

        commands.clear();
        GitClient.CommandRunner detachedHead = (arguments, directory, timeout) -> {
            commands.add(arguments);
            return success(arguments.contains("branch") ? "" : "a1b2c3d\n");
        };
        var detached = new GitClient("git", detachedHead).getCurrentBranch(project).orElseThrow();

        assertEquals("Detached HEAD · a1b2c3d", detached.displayName());
        assertEquals("rev-parse", commands.getLast().get(3));
    }

    @Test
    void countsPorcelainV2EntriesIncludingRenameOnlyOnce() {
        var output = String.join("\0",
                "1 M. N... 100644 100644 100644 abc abc file with spaces.txt",
                "2 R. N... 100644 100644 100644 abc abc R100 renamed.txt",
                "original.txt",
                "u UU N... 100644 100644 100644 100644 abc abc abc conflict.txt",
                "? untracked.txt",
                "! ignored.txt",
                ""
        ).getBytes(StandardCharsets.UTF_8);

        assertEquals(4, GitClient.countStatusEntries(output));
    }

    @Test
    void parsesLatestCommitWithUnicodeMessageAndOffsetTime() {
        var commit = GitClient.parseCommit(
                ("0123456789abcdef\0修复：支持中文路径\0"
                        + "2026-07-18T20:10:30+08:00")
                        .getBytes(StandardCharsets.UTF_8)
        ).orElseThrow();

        assertEquals("0123456789abcdef", commit.hash());
        assertEquals("修复：支持中文路径", commit.message());
        assertEquals(Instant.parse("2026-07-18T12:10:30Z"), commit.committedAt());
    }

    @Test
    void prefersOriginAndFallsBackToFirstRemote() {
        var project = Path.of("D:\\repo");
        GitClient.CommandRunner origin = (arguments, directory, timeout) ->
                success("git@example.com:team/repo.git\n");
        assertEquals("git@example.com:team/repo.git",
                new GitClient("git", origin).getRemoteUrl(project).orElseThrow());

        var commands = new ArrayList<List<String>>();
        GitClient.CommandRunner fallback = (arguments, directory, timeout) -> {
            commands.add(arguments);
            if (arguments.contains("origin")) {
                return new CommandResult(2, new byte[0], new byte[0], timeout, false);
            }
            return success(arguments.contains("get-url")
                    ? "https://example.com/team/repo.git\n"
                    : "upstream\nbackup\n");
        };

        assertEquals("https://example.com/team/repo.git",
                new GitClient("git", fallback).getRemoteUrl(project).orElseThrow());
        assertEquals("upstream", commands.getLast().getLast());
    }

    @Test
    void fetchesWithPruneAndRemoteTimeout() throws Exception {
        var command = new AtomicReference<List<String>>();
        var timeout = new AtomicReference<Duration>();
        GitClient.CommandRunner runner = (arguments, directory, commandTimeout) -> {
            command.set(arguments);
            timeout.set(commandTimeout);
            return success("");
        };

        assertTrue(new GitClient("git", runner).fetch(Path.of("D:\\repo")).successful());
        assertEquals(List.of("git", "-C", "D:\\repo", "fetch", "--prune"), command.get());
        assertEquals(Duration.ofSeconds(120), timeout.get());
    }

    @Test
    void parsesAheadAndBehindCounts() {
        assertEquals(new GitDivergence(3, 5),
                GitClient.parseDivergence("3\t5\n").orElseThrow());
        assertTrue(GitClient.parseDivergence("unknown").isEmpty());
    }

    @Test
    void listsLocalAndRemoteBranchesAndExcludesRemoteHead() {
        GitClient.CommandRunner runner = (arguments, directory, timeout) -> success(
                arguments.contains("refs/heads")
                        ? "main\nfeature/中文\n"
                        : "origin/HEAD\norigin/main\nupstream/dev\n"
        );
        var git = new GitClient("git", runner);
        var project = Path.of("D:\\repo");

        assertEquals(List.of("main", "feature/中文"), git.listLocalBranches(project));
        assertEquals(List.of("origin/main", "upstream/dev"), git.listRemoteBranches(project));
    }

    @Test
    void switchesOnlyWithSeparateBranchArguments() throws Exception {
        var command = new AtomicReference<List<String>>();
        GitClient.CommandRunner runner = (arguments, directory, timeout) -> {
            command.set(arguments);
            return success("");
        };
        var git = new GitClient("git", runner);
        var project = Path.of("D:\\repo with spaces");

        git.switchBranch(project, "feature/中文");
        assertEquals(List.of(
                "git", "-C", project.toString(), "switch", "--", "feature/中文"
        ), command.get());

        git.createTrackingBranch(project, "feature/api", "origin/feature/api");
        assertEquals(List.of(
                "git", "-C", project.toString(), "switch", "-c", "feature/api",
                "--track", "origin/feature/api"
        ), command.get());
    }

    @Test
    void pullsWithExplicitStrategyAndTimeout() throws Exception {
        var command = new AtomicReference<List<String>>();
        var timeout = new AtomicReference<Duration>();
        GitClient.CommandRunner runner = (arguments, directory, commandTimeout) -> {
            command.set(arguments);
            timeout.set(commandTimeout);
            return success("");
        };
        var git = new GitClient("git", runner);
        var project = Path.of("D:\\repo");

        git.pull(project, PullStrategy.REBASE);
        assertEquals(List.of("git", "-C", "D:\\repo", "pull", "--rebase"), command.get());
        git.pull(project, PullStrategy.MERGE);
        assertEquals("--no-rebase", command.get().getLast());
        git.pull(project, PullStrategy.GIT_CONFIG);
        assertEquals(List.of("git", "-C", "D:\\repo", "pull"), command.get());
        assertEquals(Duration.ofSeconds(180), timeout.get());
    }

    @Test
    void countsNulSeparatedConflictPaths() {
        assertEquals(2, GitClient.countNullRecords(
                "file one.txt\0目录/file two.txt\0".getBytes(StandardCharsets.UTF_8)
        ));
        assertEquals(0, GitClient.countNullRecords(new byte[0]));
    }

    private static CommandResult success(String stdout) {
        return new CommandResult(
                0,
                stdout.getBytes(StandardCharsets.UTF_8),
                new byte[0],
                Duration.ofMillis(1),
                false
        );
    }
}
