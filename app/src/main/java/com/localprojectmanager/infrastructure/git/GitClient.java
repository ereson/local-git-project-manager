package com.localprojectmanager.infrastructure.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public final class GitClient {

    private static final Duration LOCAL_COMMAND_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration PULL_TIMEOUT = Duration.ofSeconds(180);
    private static final Pattern VERSION = Pattern.compile("^git version (\\S+)");

    private final String executable;
    private final CommandRunner runner;

    public GitClient() {
        this("git", new CommandExecutor()::execute);
    }

    GitClient(String executable, CommandRunner runner) {
        this.executable = executable;
        this.runner = runner;
    }

    public Optional<GitVersion> getVersion() {
        return execute(List.of(executable, "--version"))
                .flatMap(result -> {
                    var matcher = VERSION.matcher(text(result));
                    return matcher.find()
                            ? Optional.of(new GitVersion(matcher.group(1)))
                            : Optional.empty();
                });
    }

    public Optional<GitHead> getCurrentBranch(Path projectPath) {
        var command = List.of(
                executable, "-C", projectPath.toString(), "branch", "--show-current"
        );
        var branch = execute(command).map(GitClient::text).orElse("");
        if (!branch.isBlank()) {
            return Optional.of(new GitHead(branch, false));
        }
        return execute(List.of(
                executable, "-C", projectPath.toString(), "rev-parse", "--short", "HEAD"
        )).map(GitClient::text)
                .filter(hash -> !hash.isBlank())
                .map(hash -> new GitHead(hash, true));
    }

    public OptionalInt getUncommittedFileCount(Path projectPath) {
        return execute(List.of(
                executable, "-C", projectPath.toString(),
                "status", "--porcelain=v2", "-z"
        )).map(result -> OptionalInt.of(countStatusEntries(result.stdout())))
                .orElseGet(OptionalInt::empty);
    }

    public Optional<GitCommit> getLatestCommit(Path projectPath) {
        return execute(List.of(
                executable, "-C", projectPath.toString(), "log", "-1",
                "--pretty=format:%H%x00%s%x00%cI"
        )).flatMap(result -> parseCommit(result.stdout()));
    }

    public Optional<String> getRemoteUrl(Path projectPath) {
        var prefix = List.of(executable, "-C", projectPath.toString(), "remote");
        var origin = execute(append(prefix, "get-url", "origin"))
                .map(GitClient::text)
                .filter(url -> !url.isBlank());
        if (origin.isPresent()) {
            return origin;
        }
        return execute(prefix)
                .map(GitClient::text)
                .stream()
                .flatMap(String::lines)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .findFirst()
                .flatMap(name -> execute(append(prefix, "get-url", name)))
                .map(GitClient::text)
                .filter(url -> !url.isBlank());
    }

    public CommandResult fetch(Path projectPath) throws IOException, InterruptedException {
        return runner.execute(List.of(
                executable, "-C", projectPath.toString(), "fetch", "--prune"
        ), null, FETCH_TIMEOUT);
    }

    public Optional<String> getUpstream(Path projectPath) {
        return execute(List.of(
                executable, "-C", projectPath.toString(), "rev-parse",
                "--abbrev-ref", "--symbolic-full-name", "@{upstream}"
        )).map(GitClient::text).filter(upstream -> !upstream.isBlank());
    }

    public Optional<GitDivergence> getAheadBehind(Path projectPath) {
        return execute(List.of(
                executable, "-C", projectPath.toString(), "rev-list",
                "--left-right", "--count", "HEAD...@{upstream}"
        )).flatMap(result -> parseDivergence(text(result)));
    }

    public Optional<GitConflict> getConflict(Path projectPath) {
        var conflicts = execute(List.of(
                executable, "-C", projectPath.toString(), "diff",
                "--name-only", "--diff-filter=U", "-z"
        ));
        if (conflicts.isEmpty()) {
            return Optional.empty();
        }
        var count = countNullRecords(conflicts.get().stdout());
        var gitDirectory = execute(List.of(
                executable, "-C", projectPath.toString(), "rev-parse", "--git-dir"
        )).map(GitClient::text).filter(value -> !value.isBlank());
        if (gitDirectory.isEmpty()) {
            return Optional.of(new GitConflict(
                    count > 0 ? GitConflict.Type.UNKNOWN : GitConflict.Type.NONE,
                    count
            ));
        }
        var directory = Path.of(gitDirectory.get());
        if (!directory.isAbsolute()) {
            directory = projectPath.resolve(directory);
        }
        directory = directory.normalize();
        var type = Files.exists(directory.resolve("rebase-merge"))
                || Files.exists(directory.resolve("rebase-apply"))
                ? GitConflict.Type.REBASE
                : Files.exists(directory.resolve("CHERRY_PICK_HEAD"))
                        ? GitConflict.Type.CHERRY_PICK
                        : Files.exists(directory.resolve("MERGE_HEAD")) || count > 0
                                ? GitConflict.Type.MERGE
                                : GitConflict.Type.NONE;
        return Optional.of(new GitConflict(type, count));
    }

    static int countNullRecords(byte[] output) {
        var count = 0;
        for (var value : output) {
            if (value == 0) {
                count++;
            }
        }
        return count + (output.length > 0 && output[output.length - 1] != 0 ? 1 : 0);
    }

    public List<String> listLocalBranches(Path projectPath) {
        return listRefs(projectPath, "refs/heads", false);
    }

    public List<String> listRemoteBranches(Path projectPath) {
        return listRefs(projectPath, "refs/remotes", true);
    }

    public CommandResult switchBranch(
            Path projectPath,
            String branch
    ) throws IOException, InterruptedException {
        return runner.execute(List.of(
                executable, "-C", projectPath.toString(), "switch", "--", branch
        ), null, LOCAL_COMMAND_TIMEOUT);
    }

    public CommandResult createTrackingBranch(
            Path projectPath,
            String localBranch,
            String remoteBranch
    ) throws IOException, InterruptedException {
        return runner.execute(List.of(
                executable, "-C", projectPath.toString(), "switch",
                "-c", localBranch, "--track", remoteBranch
        ), null, LOCAL_COMMAND_TIMEOUT);
    }

    public CommandResult pull(
            Path projectPath,
            com.localprojectmanager.domain.project.PullStrategy strategy
    ) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<>(List.of(
                executable, "-C", projectPath.toString(), "pull"
        ));
        switch (strategy) {
            case REBASE -> command.add("--rebase");
            case MERGE -> command.add("--no-rebase");
            case GIT_CONFIG -> {
            }
        }
        return runner.execute(command, null, PULL_TIMEOUT);
    }

    private List<String> listRefs(Path projectPath, String refs, boolean excludeHead) {
        return execute(List.of(
                executable, "-C", projectPath.toString(), "for-each-ref",
                "--format=%(refname:short)", refs
        )).map(GitClient::text)
                .stream()
                .flatMap(String::lines)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .filter(name -> !excludeHead || !name.endsWith("/HEAD"))
                .distinct()
                .toList();
    }

    static Optional<GitDivergence> parseDivergence(String output) {
        var counts = output.trim().split("\\s+");
        if (counts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new GitDivergence(
                    Integer.parseInt(counts[0]),
                    Integer.parseInt(counts[1])
            ));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static List<String> append(List<String> command, String... arguments) {
        var result = new java.util.ArrayList<>(command);
        result.addAll(List.of(arguments));
        return result;
    }

    static Optional<GitCommit> parseCommit(byte[] output) {
        var fields = new String(output, StandardCharsets.UTF_8).split("\\x00", -1);
        if (fields.length != 3 || fields[0].isBlank() || fields[2].isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new GitCommit(
                    fields[0],
                    fields[1],
                    OffsetDateTime.parse(fields[2].trim()).toInstant()
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static int countStatusEntries(byte[] output) {
        var count = 0;
        for (var offset = 0; offset < output.length;) {
            var type = output[offset];
            offset = nextRecord(output, offset);
            if (type == '1' || type == '2' || type == 'u' || type == '?') {
                count++;
            }
            if (type == '2') {
                offset = nextRecord(output, offset);
            }
        }
        return count;
    }

    private static int nextRecord(byte[] output, int offset) {
        while (offset < output.length && output[offset] != 0) {
            offset++;
        }
        return Math.min(offset + 1, output.length);
    }

    private Optional<CommandResult> execute(List<String> command) {
        try {
            var result = runner.execute(
                    command,
                    null,
                    LOCAL_COMMAND_TIMEOUT
            );
            return result.successful() ? Optional.of(result) : Optional.empty();
        } catch (IOException ignored) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static String text(CommandResult result) {
        return new String(result.stdout(), StandardCharsets.UTF_8).trim();
    }

    @FunctionalInterface
    interface CommandRunner {

        CommandResult execute(
                List<String> command,
                Path workingDirectory,
                Duration timeout
        ) throws IOException, InterruptedException;
    }
}
