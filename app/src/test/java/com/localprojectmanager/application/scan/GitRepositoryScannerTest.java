package com.localprojectmanager.application.scan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GitRepositoryScannerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void inspectsGitDirectoryAndWorktreeMetadata() throws Exception {
        var regular = Files.createDirectories(tempDirectory.resolve("regular"));
        Files.createDirectory(regular.resolve(".git"));
        assertEquals(regular.toAbsolutePath().normalize(), GitRepositoryScanner.inspect(regular).path());

        var worktree = Files.createDirectories(tempDirectory.resolve("worktree"));
        Files.writeString(worktree.resolve(".git"), "gitdir: elsewhere");
        assertEquals(worktree.toAbsolutePath().normalize(), GitRepositoryScanner.inspect(worktree).path());
    }

    @Test
    void rejectsDirectoryWithoutGitMetadata() throws Exception {
        var directory = Files.createDirectory(tempDirectory.resolve("plain"));
        assertThrows(IllegalArgumentException.class, () -> GitRepositoryScanner.inspect(directory));
    }

    @Test
    void findsGitRepositoriesMarksNearestParentAndSkipsDefaultIgnoredDirectories() throws Exception {
        var parent = Files.createDirectories(tempDirectory.resolve("parent"));
        Files.createDirectory(parent.resolve(".git"));
        var worktree = Files.createDirectories(parent.resolve("worktree"));
        Files.createFile(worktree.resolve(".git"));
        var nested = Files.createDirectories(worktree.resolve("nested"));
        Files.createDirectory(nested.resolve(".git"));
        var ignored = Files.createDirectories(tempDirectory.resolve("node_modules/repository"));
        Files.createDirectory(ignored.resolve(".git"));

        var result = GitRepositoryScanner.scan(List.of(tempDirectory), true, () -> false);
        var repositories = result.repositories().stream().collect(Collectors.toMap(
                GitRepositoryScanner.DiscoveredRepository::path,
                Function.identity()
        ));

        assertEquals(3, repositories.size());
        assertFalse(repositories.get(parent).nestedRepository());
        assertEquals(parent, repositories.get(worktree).parentRepositoryPath());
        assertEquals(worktree, repositories.get(nested).parentRepositoryPath());
        assertTrue(result.failedPaths().isEmpty());
        assertFalse(result.cancelled());
        assertTrue(GitRepositoryScanner.scan(List.of(tempDirectory), false, () -> false)
                .repositories().stream().anyMatch(repository -> repository.path().equals(ignored)));
        assertTrue(GitRepositoryScanner.scan(List.of(tempDirectory), true, () -> true).cancelled());
    }

    @Test
    void skipsCustomDirectoryNames() throws Exception {
        var ignored = Files.createDirectories(tempDirectory.resolve("generated/repository"));
        Files.createDirectory(ignored.resolve(".git"));
        assertTrue(GitRepositoryScanner.scan(
                List.of(tempDirectory), false, List.of("generated"), () -> false
        ).repositories().isEmpty());
    }
}
